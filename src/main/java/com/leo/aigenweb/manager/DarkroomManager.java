package com.leo.aigenweb.manager;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.leo.aigenweb.config.DarkroomConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 暗房图床管理器：负责与图床交互，只提供文件上传能力，不包含业务逻辑
 */
@Component
@Slf4j
public class DarkroomManager {

    @Resource
    private DarkroomConfig darkroomConfig;

    /**
     * 上传文件到图床并返回访问 URL
     *
     * @param file 要上传的文件（PNG / JPEG / GIF / WebP / AVIF，单张不超过 20MB）
     * @return 图片访问地址，失败返回 null
     */
    public String uploadFile(File file) {
        if (file == null || !file.exists()) {
            log.error("上传图床失败，文件不存在");
            return null;
        }
        try (HttpResponse response = HttpRequest.post(darkroomConfig.getBaseUrl() + "/api/v1/upload")
                .header("Authorization", "Bearer " + darkroomConfig.getToken())
                // 原文件名只用于在图床里检索，用 URL 编码避免非 ASCII 字符出问题
                .header("X-Filename", java.net.URLEncoder.encode(file.getName(), java.nio.charset.StandardCharsets.UTF_8))
                .body(FileUtil.readBytes(file))
                .timeout(darkroomConfig.getTimeoutMs())
                .execute()) {
            if (!response.isOk()) {
                log.error("上传图床失败，状态码: {}, 响应: {}", response.getStatus(), response.body());
                return null;
            }
            JSONObject image = JSONUtil.parseObj(response.body()).getJSONObject("image");
            String url = image == null ? null : image.getStr("url");
            if (StrUtil.isBlank(url)) {
                log.error("上传图床失败，响应中没有图片地址: {}", response.body());
                return null;
            }
            log.info("文件上传图床成功: {} -> {}", file.getName(), url);
            return url;
        } catch (Exception e) {
            log.error("上传图床异常: {}", file.getName(), e);
            return null;
        }
    }
}
