package com.leo.aigenweb.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.leo.aigenweb.constant.AppConstant;
import com.leo.aigenweb.model.enums.CodeGenTypeEnum;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
class AiCodeGeneratorFacadeTest {

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    /**
     * 每个用例使用唯一的 appId：对话记忆存在 Redis 里，固定 appId 会让多次运行、多个用例之间互相污染，
     * 残留的工具调用消息还会让后续请求被 LLM 接口拒绝（tool 消息前缺少 tool_calls）
     */
    private final List<Long> usedAppIds = new ArrayList<>();

    private long newAppId() {
        long appId = IdUtil.getSnowflakeNextId();
        usedAppIds.add(appId);
        return appId;
    }

    @AfterEach
    void cleanup() {
        for (Long appId : usedAppIds) {
            redisChatMemoryStore.deleteMessages(appId);
            for (CodeGenTypeEnum type : CodeGenTypeEnum.values()) {
                FileUtil.del(new File(AppConstant.CODE_OUTPUT_ROOT_DIR, type.getValue() + "_" + appId));
            }
        }
        usedAppIds.clear();
    }

    @Test
    void generateAndSaveCode() {
        File file = aiCodeGeneratorFacade.generateAndSaveCode("生成一个简单的HTML页面,40行", CodeGenTypeEnum.MULTI_FILE, newAppId());
        Assertions.assertNotNull(file);
    }

    @Test
    void generateVueProjectCodeStream() {
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                "登录界面", CodeGenTypeEnum.VUE_PROJECT, newAppId());
        // 阻塞等待所有数据收集完成
        List<String> result = codeStream.collectList().block();
        // 验证结果
        Assertions.assertNotNull(result);
        String completeContent = String.join("", result);
        Assertions.assertNotNull(completeContent);
    }
}
