package com.leo.aigenweb.langgraph4j.node.concurrent;

import com.leo.aigenweb.langgraph4j.model.ImageCollectionPlan;
import com.leo.aigenweb.langgraph4j.model.ImageResource;
import com.leo.aigenweb.langgraph4j.state.WorkflowContext;
import com.leo.aigenweb.langgraph4j.tools.ImageSearchTool;
import com.leo.aigenweb.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 内容图片收集节点（并发分支中的一个节点）
 */
@Slf4j
public class ContentImageCollectorNode {

    private ContentImageCollectorNode() {
    }

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            List<ImageResource> contentImages = new ArrayList<>();
            try {
                ImageCollectionPlan plan = context.getImageCollectionPlan();
                if (plan != null && plan.getContentImageTasks() != null) {
                    ImageSearchTool tool = SpringContextUtil.getBean(ImageSearchTool.class);
                    log.info("开始收集内容图片，任务数: {}", plan.getContentImageTasks().size());
                    for (ImageCollectionPlan.ImageSearchTask task : plan.getContentImageTasks()) {
                        List<ImageResource> images = tool.searchContentImages(task.query());
                        if (images != null) {
                            contentImages.addAll(images);
                        }
                    }
                    log.info("内容图片收集完成，共 {} 张", contentImages.size());
                }
            } catch (Exception e) {
                log.error("内容图片收集失败: {}", e.getMessage(), e);
            }
            // 并发分支各自写入自己的中间字段，互不覆盖，最后由聚合节点汇总
            context.setContentImages(contentImages);
            return WorkflowContext.saveContext(context);
        });
    }
}
