package com.leo.aigenweb.langgraph4j.ai;

import com.leo.aigenweb.langgraph4j.model.ImageCollectionPlan;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 图片收集规划 AI 服务：先让 AI 规划需要哪些图片，再由程序并发收集（比让 AI 反复调用工具更快、更省 token）
 */
public interface ImageCollectionPlanService {

    /**
     * 根据用户提示词分析需要收集的图片类型和参数
     */
    @SystemMessage(fromResource = "prompt/image-collection-plan-system-prompt.txt")
    ImageCollectionPlan planImageCollection(@UserMessage String userPrompt);
}
