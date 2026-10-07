package com.leo.aigenweb.ratelimit;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 限流 + SSE 错误响应的集成测试（真实 Redis）。
 * 请求不带登录态，所以限流按 IP；前 5 次会因为未登录被业务拒绝（但仍然消耗令牌），第 6 次应该被限流。
 */
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    private static final String LIMIT_KEY = "rate_limit:chat:ip:127.0.0.1";

    @Resource
    private MockMvc mockMvc;

    @Resource
    private RedissonClient redissonClient;

    @BeforeEach
    @AfterEach
    void cleanLimiter() {
        redissonClient.getKeys().delete(LIMIT_KEY);
    }

    private String callChat() throws Exception {
        MvcResult result = mockMvc.perform(get("/app/chat/gen/code")
                        .param("appId", "1").param("message", "做一个博客")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andReturn();
        assertTrue(result.getResponse().getContentType().startsWith("text/event-stream"),
                "SSE 请求的错误也要以 event-stream 返回，实际: " + result.getResponse().getContentType());
        return result.getResponse().getContentAsString();
    }

    private JSONObject businessErrorOf(String sse) {
        assertTrue(sse.contains("event: business-error"), sse);
        assertTrue(sse.trim().endsWith("event: done\ndata: {}"), "错误事件之后要有 done 事件: " + sse);
        String dataLine = sse.lines().filter(l -> l.startsWith("data: {\"")).findFirst().orElseThrow();
        return JSONUtil.parseObj(dataLine.substring("data: ".length()));
    }

    @Test
    void sixthRequestInAMinuteIsRateLimited() throws Exception {
        for (int i = 1; i <= 5; i++) {
            JSONObject error = businessErrorOf(callChat());
            assertEquals(40100, error.getInt("code"), "第 " + i + " 次应该是未登录而不是限流");
        }
        JSONObject limited = businessErrorOf(callChat());
        assertEquals(42900, limited.getInt("code"));
        assertEquals("AI 对话请求过于频繁，请稍后再试", limited.getStr("message"));
    }

    @Test
    void limiterKeyExpires() throws Exception {
        callChat();
        long ttl = redissonClient.getBucket(LIMIT_KEY).remainTimeToLive();
        // 限流器必须设置过期时间，否则 Redis 里的 key 永不过期
        assertTrue(ttl > 0 && ttl <= TimeUnit.HOURS.toMillis(1), "ttl=" + ttl);
    }

    @Test
    void nonSseRequestStillGetsJsonError() throws Exception {
        MvcResult result = mockMvc.perform(get("/app/chat/gen/code")
                        .param("appId", "1").param("message", "做一个博客")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        // 普通请求（没有 Accept: text/event-stream）不应该写 SSE 格式
        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("event: business-error"), body);
    }
}
