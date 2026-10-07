package com.leo.aigenweb.langgraph4j.node.concurrent;

import com.leo.aigenweb.langgraph4j.model.ImageCollectionPlan;
import com.leo.aigenweb.langgraph4j.model.ImageResource;
import com.leo.aigenweb.langgraph4j.state.WorkflowContext;
import com.leo.aigenweb.langgraph4j.tools.MermaidDiagramTool;
import com.leo.aigenweb.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 架构图绘制节点（并发分支中的一个节点）
 */
@Slf4j
public class DiagramCollectorNode {

    private DiagramCollectorNode() {
    }

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            List<ImageResource> diagrams = new ArrayList<>();
            try {
                ImageCollectionPlan plan = context.getImageCollectionPlan();
                if (plan != null && plan.getDiagramTasks() != null) {
                    MermaidDiagramTool tool = SpringContextUtil.getBean(MermaidDiagramTool.class);
                    log.info("开始收集架构图，任务数: {}", plan.getDiagramTasks().size());
                    for (ImageCollectionPlan.DiagramTask task : plan.getDiagramTasks()) {
                        List<ImageResource> images = tool.generateMermaidDiagram(task.mermaidCode(), task.description());
                        if (images != null) {
                            diagrams.addAll(images);
                        }
                    }
                    log.info("架构图收集完成，共 {} 张", diagrams.size());
                }
            } catch (Exception e) {
                log.error("架构图收集失败: {}", e.getMessage(), e);
            }
            // 并发分支各自写入自己的中间字段，互不覆盖，最后由聚合节点汇总
            context.setDiagrams(diagrams);
            return WorkflowContext.saveContext(context);
        });
    }
}
