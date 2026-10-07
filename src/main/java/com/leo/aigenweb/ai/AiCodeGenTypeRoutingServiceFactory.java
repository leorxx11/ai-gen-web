package com.leo.aigenweb.ai;

import com.leo.aigenweb.utils.SpringContextUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 代码生成类型路由服务工厂
 */
@Slf4j
@Component
public class AiCodeGenTypeRoutingServiceFactory {

    /**
     * 创建 AI 代码生成类型路由服务实例。
     * 每次都取一个新的多例路由模型，多个请求并发路由时不会互相阻塞。
     */
    public AiCodeGenTypeRoutingService createAiCodeGenTypeRoutingService() {
        ChatModel chatModel = SpringContextUtil.getBean("routingChatModelPrototype", ChatModel.class);
        return AiServices.builder(AiCodeGenTypeRoutingService.class)
                .chatModel(chatModel)
                .build();
    }
}
