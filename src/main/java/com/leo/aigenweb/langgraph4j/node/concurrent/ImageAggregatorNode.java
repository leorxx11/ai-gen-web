package com.leo.aigenweb.langgraph4j.node.concurrent;

import com.leo.aigenweb.langgraph4j.model.ImageResource;
import com.leo.aigenweb.langgraph4j.node.ImageCollectorNode;
import com.leo.aigenweb.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 图片聚合节点：汇聚所有并发分支收集到的图片
 */
@Slf4j
public class ImageAggregatorNode {

    private ImageAggregatorNode() {
    }

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 图片聚合");
            List<ImageResource> allImages = new ArrayList<>();
            // 从各个中间字段聚合图片
            addAll(allImages, context.getContentImages());
            addAll(allImages, context.getIllustrations());
            addAll(allImages, context.getDiagrams());
            addAll(allImages, context.getLogos());
            log.info("图片聚合完成，总共 {} 张图片", allImages.size());
            context.setImageList(allImages.size() > ImageCollectorNode.MAX_IMAGES
                    ? new ArrayList<>(allImages.subList(0, ImageCollectorNode.MAX_IMAGES)) : allImages);
            context.setCurrentStep("图片聚合");
            return WorkflowContext.saveContext(context);
        });
    }

    private static void addAll(List<ImageResource> target, List<ImageResource> source) {
        if (source != null) {
            target.addAll(source);
        }
    }
}
