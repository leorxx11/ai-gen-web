package com.leo.aigenweb.langgraph4j.node.concurrent;

import com.leo.aigenweb.langgraph4j.ai.ImageCollectionPlanService;
import com.leo.aigenweb.langgraph4j.model.ImageCollectionPlan;
import com.leo.aigenweb.langgraph4j.state.WorkflowContext;
import com.leo.aigenweb.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 图片计划节点：分析用户需求，生成图片收集计划，为并发分支做准备
 */
@Slf4j
public class ImagePlanNode {

    private ImagePlanNode() {
    }

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 图片计划");
            try {
                ImageCollectionPlanService planService = SpringContextUtil.getBean(ImageCollectionPlanService.class);
                ImageCollectionPlan plan = planService.planImageCollection(context.getOriginalPrompt());
                log.info("生成图片收集计划，准备启动并发分支");
                context.setImageCollectionPlan(plan);
            } catch (Exception e) {
                // 没有计划时各收集节点会得到空结果，工作流照常继续
                log.error("图片计划生成失败: {}", e.getMessage(), e);
            }
            context.setCurrentStep("图片计划");
            return WorkflowContext.saveContext(context);
        });
    }
}
