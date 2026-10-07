package com.leo.aigenweb.langgraph4j;

import com.leo.aigenweb.exception.BusinessException;
import com.leo.aigenweb.exception.ErrorCode;
import com.leo.aigenweb.langgraph4j.node.CodeGeneratorNode;
import com.leo.aigenweb.langgraph4j.node.CodeQualityCheckNode;
import com.leo.aigenweb.langgraph4j.node.ProjectBuilderNode;
import com.leo.aigenweb.langgraph4j.node.PromptEnhancerNode;
import com.leo.aigenweb.langgraph4j.node.RouterNode;
import com.leo.aigenweb.langgraph4j.node.concurrent.ContentImageCollectorNode;
import com.leo.aigenweb.langgraph4j.node.concurrent.DiagramCollectorNode;
import com.leo.aigenweb.langgraph4j.node.concurrent.IllustrationCollectorNode;
import com.leo.aigenweb.langgraph4j.node.concurrent.ImageAggregatorNode;
import com.leo.aigenweb.langgraph4j.node.concurrent.ImagePlanNode;
import com.leo.aigenweb.langgraph4j.node.concurrent.LogoCollectorNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.leo.aigenweb.langgraph4j.WorkflowNodeNames.*;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * 并发版代码生成工作流：用 LangGraph4j 的并发分支能力让 4 类图片收集节点同时执行
 * <p>
 * 流程：图片计划 -> (内容图片 / 插画 / 架构图 / Logo 并发收集) -> 图片聚合 -> 提示词增强 -> ... 后续与串行版一致
 */
public class CodeGenConcurrentWorkflow extends AbstractCodeGenWorkflow {

    /**
     * 并发分支使用的执行器。图片收集是 IO 密集型任务，用虚拟线程即可，无需自己维护线程池大小
     */
    private static final Executor PARALLEL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public CompiledGraph<MessagesState<String>> createWorkflow() {
        // 关闭图片收集时没有可并发的分支，直接使用不含图片收集的串行流程
        if (!WorkflowSettings.isImageCollectionEnabled()) {
            return new CodeGenWorkflow().createWorkflow();
        }
        try {
            return compile(new MessagesStateGraph<String>()
                    .addNode(IMAGE_PLAN, ImagePlanNode.create())
                    .addNode(PROMPT_ENHANCER, PromptEnhancerNode.create())
                    .addNode(ROUTER, RouterNode.create())
                    .addNode(CODE_GENERATOR, CodeGeneratorNode.create())
                    .addNode(CODE_QUALITY_CHECK, CodeQualityCheckNode.create())
                    .addNode(PROJECT_BUILDER, ProjectBuilderNode.create())
                    // 并发图片收集节点
                    .addNode(CONTENT_IMAGE_COLLECTOR, ContentImageCollectorNode.create())
                    .addNode(ILLUSTRATION_COLLECTOR, IllustrationCollectorNode.create())
                    .addNode(DIAGRAM_COLLECTOR, DiagramCollectorNode.create())
                    .addNode(LOGO_COLLECTOR, LogoCollectorNode.create())
                    .addNode(IMAGE_AGGREGATOR, ImageAggregatorNode.create())
                    .addEdge(START, IMAGE_PLAN)
                    // 并发分支：同一节点连到多个不同节点，框架自动当作并发分支处理
                    .addEdge(IMAGE_PLAN, CONTENT_IMAGE_COLLECTOR)
                    .addEdge(IMAGE_PLAN, ILLUSTRATION_COLLECTOR)
                    .addEdge(IMAGE_PLAN, DIAGRAM_COLLECTOR)
                    .addEdge(IMAGE_PLAN, LOGO_COLLECTOR)
                    // 汇聚：所有收集节点都汇聚到聚合节点，聚合节点会等所有分支完成
                    .addEdge(CONTENT_IMAGE_COLLECTOR, IMAGE_AGGREGATOR)
                    .addEdge(ILLUSTRATION_COLLECTOR, IMAGE_AGGREGATOR)
                    .addEdge(DIAGRAM_COLLECTOR, IMAGE_AGGREGATOR)
                    .addEdge(LOGO_COLLECTOR, IMAGE_AGGREGATOR)
                    // 继续串行流程
                    .addEdge(IMAGE_AGGREGATOR, PROMPT_ENHANCER)
                    .addEdge(PROMPT_ENHANCER, ROUTER)
                    .addEdge(ROUTER, CODE_GENERATOR)
                    .addEdge(CODE_GENERATOR, CODE_QUALITY_CHECK)
                    .addConditionalEdges(CODE_QUALITY_CHECK,
                            edge_async(WorkflowRouting::routeAfterQualityCheck),
                            Map.of(
                                    ROUTE_BUILD, PROJECT_BUILDER,
                                    ROUTE_SKIP_BUILD, END,
                                    ROUTE_FAIL, CODE_GENERATOR))
                    .addEdge(PROJECT_BUILDER, END), "并发代码生成工作流");
        } catch (GraphStateException e) {
            throw new BusinessException(
                    ErrorCode.OPERATION_ERROR, "并发代码生成工作流创建失败");
        }
    }

    /**
     * 并发分支默认串行执行，必须给分支起点节点配置执行器才会真正并发
     */
    @Override
    protected RunnableConfig buildRunnableConfig() {
        return RunnableConfig.builder()
                .addParallelNodeExecutor(IMAGE_PLAN, PARALLEL_EXECUTOR)
                .build();
    }
}
