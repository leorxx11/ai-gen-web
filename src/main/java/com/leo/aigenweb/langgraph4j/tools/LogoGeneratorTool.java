package com.leo.aigenweb.langgraph4j.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.leo.aigenweb.langgraph4j.model.ImageResource;
import com.leo.aigenweb.langgraph4j.model.enums.ImageCategoryEnum;
import com.leo.aigenweb.manager.DarkroomManager;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Logo 图片生成工具：调用阿里云百炼（DashScope）文生图接口生成 Logo。
 * <p>
 * 直接使用 HTTP 接口而不是 SDK，避免引入额外依赖。文生图是异步任务：先提交，再轮询任务状态。
 * 生成的图片链接仅 24 小时有效，所以会下载后转存到图床。
 */
@Slf4j
@Component
public class LogoGeneratorTool {

    @Value("${dashscope.base-url:https://dashscope.aliyuncs.com}")
    private String baseUrl;

    @Value("${dashscope.api-key:}")
    private String dashScopeApiKey;

    @Value("${dashscope.image-model:wan2.2-t2i-flash}")
    private String imageModel;

    /**
     * 轮询任务状态的最大次数与间隔
     */
    @Value("${dashscope.poll-max-attempts:30}")
    private int pollMaxAttempts;

    @Value("${dashscope.poll-interval-ms:2000}")
    private long pollIntervalMs;

    @Resource
    private DarkroomManager darkroomManager;

    @Tool("根据描述生成 Logo 设计图片，用于网站品牌标识")
    public List<ImageResource> generateLogos(@P("Logo 设计描述，如名称、行业、风格等，尽量详细") String description) {
        List<ImageResource> logoList = new ArrayList<>();
        if (StrUtil.hasBlank(description, dashScopeApiKey)) {
            log.warn("DashScope 未配置 api-key 或描述为空，跳过 Logo 生成");
            return logoList;
        }
        File imageFile = null;
        try {
            String taskId = submitTask(description);
            if (taskId == null) {
                return logoList;
            }
            String imageUrl = waitForResult(taskId);
            if (imageUrl == null) {
                return logoList;
            }
            // 生成结果的链接 24 小时后失效，下载后转存到自己的图床
            imageFile = FileUtil.createTempFile("logo_", ".png", true);
            long size = HttpRequest.get(imageUrl).timeout(30000).executeAsync().writeBody(imageFile);
            if (size <= 0) {
                log.error("Logo 下载失败: {}", imageUrl);
                return logoList;
            }
            String url = darkroomManager.uploadFile(imageFile);
            if (StrUtil.isNotBlank(url)) {
                logoList.add(ImageResource.builder()
                        .category(ImageCategoryEnum.LOGO)
                        .description(description)
                        .url(url)
                        .build());
            }
        } catch (Exception e) {
            log.error("生成 Logo 失败: {}", e.getMessage(), e);
        } finally {
            FileUtil.del(imageFile);
        }
        return logoList;
    }

    /**
     * 提交文生图任务，返回 taskId
     */
    private String submitTask(String description) {
        // 构建 Logo 设计提示词
        String logoPrompt = String.format("生成 Logo，Logo 中禁止包含任何文字！Logo 介绍：%s", description);
        JSONObject body = JSONUtil.createObj()
                .set("model", imageModel)
                .set("input", Map.of("prompt", logoPrompt))
                // 生成 1 张足够，因为 AI 不知道哪张最好
                .set("parameters", Map.of("size", "512*512", "n", 1));
        try (HttpResponse response = HttpRequest.post(baseUrl + "/api/v1/services/aigc/text2image/image-synthesis")
                .header("Authorization", "Bearer " + dashScopeApiKey)
                .header("X-DashScope-Async", "enable")
                .header("Content-Type", "application/json")
                .body(body.toString())
                .timeout(15000)
                .execute()) {
            if (!response.isOk()) {
                log.error("提交 Logo 生成任务失败，状态码: {}, 响应: {}", response.getStatus(), response.body());
                return null;
            }
            JSONObject output = JSONUtil.parseObj(response.body()).getJSONObject("output");
            return output == null ? null : output.getStr("task_id");
        }
    }

    /**
     * 轮询任务，成功返回图片地址，失败或超时返回 null
     */
    private String waitForResult(String taskId) throws InterruptedException {
        for (int i = 0; i < pollMaxAttempts; i++) {
            try (HttpResponse response = HttpRequest.get(baseUrl + "/api/v1/tasks/" + taskId)
                    .header("Authorization", "Bearer " + dashScopeApiKey)
                    .timeout(15000)
                    .execute()) {
                if (response.isOk()) {
                    JSONObject output = JSONUtil.parseObj(response.body()).getJSONObject("output");
                    String status = output == null ? null : output.getStr("task_status");
                    if ("SUCCEEDED".equals(status)) {
                        JSONArray results = output.getJSONArray("results");
                        if (results != null && !results.isEmpty()) {
                            return results.getJSONObject(0).getStr("url");
                        }
                        return null;
                    }
                    if ("FAILED".equals(status) || "CANCELED".equals(status) || "UNKNOWN".equals(status)) {
                        log.error("Logo 生成任务失败，状态: {}, 响应: {}", status, response.body());
                        return null;
                    }
                }
            }
            Thread.sleep(pollIntervalMs);
        }
        log.error("Logo 生成任务超时: {}", taskId);
        return null;
    }
}
