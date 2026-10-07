package com.leo.aigenweb.langgraph4j.node.concurrent;

import com.leo.aigenweb.langgraph4j.model.ImageCollectionPlan;
import com.leo.aigenweb.langgraph4j.model.ImageResource;
import com.leo.aigenweb.langgraph4j.state.WorkflowContext;
import com.leo.aigenweb.langgraph4j.tools.LogoGeneratorTool;
import com.leo.aigenweb.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Logo 生成节点（并发分支中的一个节点）
 */
@Slf4j
public class LogoCollectorNode {

    private LogoCollectorNode() {
    }

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            List<ImageResource> logos = new ArrayList<>();
            try {
                ImageCollectionPlan plan = context.getImageCollectionPlan();
                if (plan != null && plan.getLogoTasks() != null) {
                    LogoGeneratorTool tool = SpringContextUtil.getBean(LogoGeneratorTool.class);
                    log.info("开始收集Logo，任务数: {}", plan.getLogoTasks().size());
                    for (ImageCollectionPlan.LogoTask task : plan.getLogoTasks()) {
                        List<ImageResource> images = tool.generateLogos(task.description());
                        if (images != null) {
                            logos.addAll(images);
                        }
                    }
                    log.info("Logo收集完成，共 {} 张", logos.size());
                }
            } catch (Exception e) {
                log.error("Logo收集失败: {}", e.getMessage(), e);
            }
            // 并发分支各自写入自己的中间字段，互不覆盖，最后由聚合节点汇总
            context.setLogos(logos);
            return WorkflowContext.saveContext(context);
        });
    }
}
