package com.leo.aigenweb.langgraph4j.node;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.leo.aigenweb.langgraph4j.model.ImageResource;
import com.leo.aigenweb.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 提示词增强节点：把收集到的图片信息拼接到原始提示词后，引导 AI 把这些图片用作网站素材
 */
@Slf4j
public class PromptEnhancerNode {

    private PromptEnhancerNode() {
    }

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 提示词增强");
            String enhancedPrompt = buildEnhancedPrompt(
                    context.getOriginalPrompt(), context.getImageList(), context.getImageListStr());
            context.setCurrentStep("提示词增强");
            context.setEnhancedPrompt(enhancedPrompt);
            log.info("提示词增强完成，增强后长度: {} 字符", enhancedPrompt.length());
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 构建增强后的提示词，兼容图片对象列表和图片描述字符串两种来源
     */
    static String buildEnhancedPrompt(String originalPrompt, List<ImageResource> imageList, String imageListStr) {
        StringBuilder builder = new StringBuilder(StrUtil.nullToEmpty(originalPrompt));
        if (CollUtil.isEmpty(imageList) && StrUtil.isBlank(imageListStr)) {
            return builder.toString();
        }
        builder.append("\n\n## 可用素材资源\n");
        builder.append("请在生成网站使用以下图片资源，将这些图片合理地嵌入到网站的相应位置中。\n");
        if (CollUtil.isNotEmpty(imageList)) {
            for (ImageResource image : imageList) {
                builder.append("- ")
                        .append(image.getCategory() == null ? "图片" : image.getCategory().getText())
                        .append("：")
                        .append(image.getDescription())
                        .append("（")
                        .append(image.getUrl())
                        .append("）\n");
            }
        } else {
            builder.append(imageListStr);
        }
        return builder.toString();
    }
}
