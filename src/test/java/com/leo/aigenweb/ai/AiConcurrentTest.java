package com.leo.aigenweb.ai;

import com.leo.aigenweb.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发调用 AI（真实大模型）：用虚拟线程同时发起多个路由请求，观察总耗时是否接近“最慢的一个”而不是“全部相加”
 */
@Slf4j
@SpringBootTest
class AiConcurrentTest {

    @Resource
    private AiCodeGenTypeRoutingServiceFactory routingServiceFactory;

    @Resource(name = "openAiChatModel")
    private ChatModel singletonChatModel;

    private static final String[] PROMPTS = {"做一个简单的HTML页面", "做一个多页面网站项目", "做一个Vue管理系统", "做一个个人简历页", "做一个带路由的后台管理系统"};

    /**
     * 并发执行 PROMPTS，返回 {总耗时, 各请求耗时之和}
     */
    private long[] runConcurrently(Supplier<AiCodeGenTypeRoutingService> serviceSupplier) throws InterruptedException {
        AtomicLong sum = new AtomicLong();
        List<Thread> threads = new ArrayList<>();
        long begin = System.nanoTime();
        for (String prompt : PROMPTS) {
            threads.add(Thread.ofVirtual().start(() -> {
                long start = System.nanoTime();
                CodeGenTypeEnum result = serviceSupplier.get().routeCodeGenType(prompt);
                long cost = (System.nanoTime() - start) / 1_000_000;
                sum.addAndGet(cost);
                log.info("{} -> {} ({} ms)", prompt, result.getValue(), cost);
            }));
        }
        for (Thread thread : threads) {
            thread.join();
        }
        return new long[]{(System.nanoTime() - begin) / 1_000_000, sum.get()};
    }

    @Test
    void routingCallsRunInParallelWithPrototypeModels() throws InterruptedException {
        long[] r = runConcurrently(routingServiceFactory::createAiCodeGenTypeRoutingService);
        log.info("【多例模型】总耗时 {} ms，各请求耗时之和 {} ms", r[0], r[1]);
        assertTrue(r[0] < r[1] * 0.7, "并发总耗时 " + r[0] + "ms 应明显小于串行耗时之和 " + r[1] + "ms");
    }

    @Test
    void prototypeModelsAreDistinctInstances() {
        assertEquals(false, routingServiceFactory.createAiCodeGenTypeRoutingService()
                == routingServiceFactory.createAiCodeGenTypeRoutingService());
    }

    /**
     * 对照组：共用同一个单例模型。仅用于观察，不做断言（是否串行取决于底层 HTTP 客户端）
     */
    @Test
    void singletonModelComparison() throws InterruptedException {
        long[] r = runConcurrently(() -> AiServices.builder(AiCodeGenTypeRoutingService.class)
                .chatModel(singletonChatModel).build());
        log.info("【单例模型对照】总耗时 {} ms，各请求耗时之和 {} ms", r[0], r[1]);
    }
}
