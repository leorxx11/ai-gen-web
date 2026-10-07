package com.leo.aigenweb.langgraph4j.node.concurrent;

import com.leo.aigenweb.langgraph4j.model.ImageCollectionPlan;
import com.leo.aigenweb.langgraph4j.model.ImageResource;
import com.leo.aigenweb.langgraph4j.state.WorkflowContext;
import com.leo.aigenweb.langgraph4j.tools.UndrawIllustrationTool;
import com.leo.aigenweb.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 插画图片收集节点（并发分支中的一个节点）
 */
@Slf4j
public class IllustrationCollectorNode {

    private IllustrationCollectorNode() {
    }

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            List<ImageResource> illustrations = new ArrayList<>();
            try {
                ImageCollectionPlan plan = context.getImageCollectionPlan();
                if (plan != null && plan.getIllustrationTasks() != null) {
                    UndrawIllustrationTool tool = SpringContextUtil.getBean(UndrawIllustrationTool.class);
                    log.info("开始收集插画图片，任务数: {}", plan.getIllustrationTasks().size());
                    for (ImageCollectionPlan.IllustrationTask task : plan.getIllustrationTasks()) {
                        List<ImageResource> images = tool.searchIllustrations(task.query());
                        if (images != null) {
                            illustrations.addAll(images);
                        }
                    }
                    log.info("插画图片收集完成，共 {} 张", illustrations.size());
                }
            } catch (Exception e) {
                log.error("插画图片收集失败: {}", e.getMessage(), e);
            }
            // 并发分支各自写入自己的中间字段，互不覆盖，最后由聚合节点汇总
            context.setIllustrations(illustrations);
            return WorkflowContext.saveContext(context);
        });
    }
}
