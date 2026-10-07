package com.leo.aigenweb.langgraph4j;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.leo.aigenweb.exception.BusinessException;
import com.leo.aigenweb.exception.ErrorCode;
import com.leo.aigenweb.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 代码生成工作流基类：封装“编译图 + 执行 + 同步/SSE 流式输出”，子类只负责定义图的结构
 */
@Slf4j
public abstract class AbstractCodeGenWorkflow {

    /**
     * 创建并编译工作流图
     */
    public abstract CompiledGraph<MessagesState<String>> createWorkflow();

    /**
     * 运行时配置，子类可覆盖（如配置并发分支的线程池）
     */
    protected RunnableConfig buildRunnableConfig() {
        return RunnableConfig.builder().build();
    }

    /**
     * 编译图，受检异常转为业务异常
     */
    protected static CompiledGraph<MessagesState<String>> compile(
            StateGraph<MessagesState<String>> graph, String name) {
        try {
            return graph.compile();
        } catch (GraphStateException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, name + "创建失败");
        }
    }

    /**
     * 同步执行工作流，返回最终上下文
     */
    public WorkflowContext executeWorkflow(String originalPrompt) {
        return executeWorkflow(originalPrompt, null);
    }

    /**
     * 同步执行工作流
     *
     * @param appId 应用 ID，为空时自动分配唯一值（避免多次执行共用目录和对话记忆）
     */
    public WorkflowContext executeWorkflow(String originalPrompt, Long appId) {
        CompiledGraph<MessagesState<String>> workflow = createWorkflow();
        WorkflowContext initialContext = newContext(originalPrompt, appId);
        log.info("工作流图:\n{}", workflow.getGraph(GraphRepresentation.Type.MERMAID).content());
        log.info("开始执行代码生成工作流");
        WorkflowContext finalContext = initialContext;
        int stepCounter = 1;
        for (NodeOutput<MessagesState<String>> step : workflow.stream(
                Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext), buildRunnableConfig())) {
            log.info("--- 第 {} 步完成 ---", stepCounter);
            WorkflowContext currentContext = WorkflowContext.getContext(step.state());
            if (currentContext != null) {
                finalContext = currentContext;
                log.info("当前步骤上下文: {}", currentContext);
            }
            stepCounter++;
        }
        log.info("代码生成工作流执行完成！");
        return finalContext;
    }

    /**
     * 流式执行工作流：每完成一个步骤推送一个 SSE 事件
     */
    public Flux<ServerSentEvent<String>> executeWorkflowWithFlux(String originalPrompt, Long appId) {
        return Flux.create(sink -> Thread.startVirtualThread(() -> {
            try {
                CompiledGraph<MessagesState<String>> workflow = createWorkflow();
                WorkflowContext initialContext = newContext(originalPrompt, appId);
                sink.next(sse("workflow_start", Map.of(
                        "message", "开始执行代码生成工作流",
                        "originalPrompt", originalPrompt)));
                int stepCounter = 1;
                WorkflowContext finalContext = initialContext;
                for (NodeOutput<MessagesState<String>> step : workflow.stream(
                        Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext), buildRunnableConfig())) {
                    // 客户端断开后不再继续往下推送
                    if (sink.isCancelled()) {
                        log.info("客户端已断开，停止推送工作流事件");
                        return;
                    }
                    WorkflowContext currentContext = WorkflowContext.getContext(step.state());
                    if (currentContext != null) {
                        finalContext = currentContext;
                        // node 是刚执行完的节点名；并发分支共用同一个上下文，currentStep 只能反映最近一次写入
                        sink.next(sse("step_completed", Map.of(
                                "stepNumber", stepCounter,
                                "node", String.valueOf(step.node()),
                                "currentStep", String.valueOf(currentContext.getCurrentStep()))));
                    }
                    stepCounter++;
                }
                sink.next(sse("workflow_completed", Map.of(
                        "message", "代码生成工作流执行完成！",
                        // 只返回前端需要的信息，不暴露服务器上的目录路径
                        "appId", String.valueOf(finalContext.getAppId()),
                        "codeGenType", finalContext.getGenerationType() == null
                                ? "" : finalContext.getGenerationType().getValue())));
                sink.complete();
            } catch (Exception e) {
                log.error("工作流执行失败: {}", e.getMessage(), e);
                sink.next(sse("workflow_error", Map.of("message", "工作流执行失败")));
                sink.complete();
            }
        }));
    }

    private WorkflowContext newContext(String originalPrompt, Long appId) {
        return WorkflowContext.builder()
                .appId(appId != null ? appId : IdUtil.getSnowflakeNextId())
                .originalPrompt(originalPrompt)
                .currentStep("初始化")
                .build();
    }

    private ServerSentEvent<String> sse(String eventType, Object data) {
        return ServerSentEvent.<String>builder().event(eventType).data(JSONUtil.toJsonStr(data)).build();
    }
}
