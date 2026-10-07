package com.leo.aigenweb.langgraph4j;

import com.leo.aigenweb.exception.BusinessException;
import com.leo.aigenweb.exception.ErrorCode;
import com.leo.aigenweb.langgraph4j.node.CodeGeneratorNode;
import com.leo.aigenweb.langgraph4j.node.CodeQualityCheckNode;
import com.leo.aigenweb.langgraph4j.node.ImageCollectorNode;
import com.leo.aigenweb.langgraph4j.node.ProjectBuilderNode;
import com.leo.aigenweb.langgraph4j.node.PromptEnhancerNode;
import com.leo.aigenweb.langgraph4j.node.RouterNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;

import java.util.Map;

import static com.leo.aigenweb.langgraph4j.WorkflowNodeNames.*;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * 代码生成工作流（图片收集在节点内部并发）
 * <p>
 * 流程：（可选）图片收集 -> 提示词增强 -> 智能路由 -> 代码生成 -> 质量检查 --(通过且为 Vue)--> 项目构建 -> 结束
 * 质检失败回到代码生成重试（有次数上限），通过且无需构建则直接结束。
 */
public class CodeGenWorkflow extends AbstractCodeGenWorkflow {

    @Override
    public CompiledGraph<MessagesState<String>> createWorkflow() {
        // 图片收集可通过配置关闭：关闭时不加入图片收集节点，直接从提示词增强开始
        boolean withImages = WorkflowSettings.isImageCollectionEnabled();
        try {
            StateGraph<MessagesState<String>> graph = new MessagesStateGraph<String>()
                    // 添加节点
                    .addNode(PROMPT_ENHANCER, PromptEnhancerNode.create())
                    .addNode(ROUTER, RouterNode.create())
                    .addNode(CODE_GENERATOR, CodeGeneratorNode.create())
                    .addNode(CODE_QUALITY_CHECK, CodeQualityCheckNode.create())
                    .addNode(PROJECT_BUILDER, ProjectBuilderNode.create());
            if (withImages) {
                graph.addNode(IMAGE_COLLECTOR, ImageCollectorNode.create())
                        .addEdge(START, IMAGE_COLLECTOR)
                        .addEdge(IMAGE_COLLECTOR, PROMPT_ENHANCER);
            } else {
                graph.addEdge(START, PROMPT_ENHANCER);
            }
            graph.addEdge(PROMPT_ENHANCER, ROUTER)
                    .addEdge(ROUTER, CODE_GENERATOR)
                    .addEdge(CODE_GENERATOR, CODE_QUALITY_CHECK)
                    // 质检条件边：根据质检结果决定下一步
                    .addConditionalEdges(CODE_QUALITY_CHECK,
                            edge_async(WorkflowRouting::routeAfterQualityCheck),
                            Map.of(
                                    ROUTE_BUILD, PROJECT_BUILDER,
                                    ROUTE_SKIP_BUILD, END,
                                    ROUTE_FAIL, CODE_GENERATOR))
                    .addEdge(PROJECT_BUILDER, END);
            return compile(graph, "代码生成工作流");
        } catch (GraphStateException e) {
            throw new BusinessException(
                    ErrorCode.OPERATION_ERROR, "代码生成工作流创建失败");
        }
    }
}
