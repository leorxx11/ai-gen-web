package com.leo.aigenweb.langgraph4j.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.leo.aigenweb.langgraph4j.model.ImageResource;
import com.leo.aigenweb.langgraph4j.model.enums.ImageCategoryEnum;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 内容图片搜索工具：通过 Pexels 免费图片库按关键词搜索图片
 */
@Slf4j
@Component
public class ImageSearchTool {

    @Value("${pexels.base-url:https://api.pexels.com}")
    private String pexelsBaseUrl;

    @Value("${pexels.api-key:}")
    private String pexelsApiKey;

    @Tool("搜索内容相关的图片，用于网站内容展示")
    public List<ImageResource> searchContentImages(@P("搜索关键词") String query) {
        List<ImageResource> imageList = new ArrayList<>();
        if (StrUtil.hasBlank(query, pexelsApiKey)) {
            log.warn("Pexels 未配置 api-key 或关键词为空，跳过内容图片搜索");
            return imageList;
        }
        int searchCount = 12;
        // 调用 API，注意释放资源
        try (HttpResponse response = HttpRequest.get(pexelsBaseUrl + "/v1/search")
                .header("Authorization", pexelsApiKey)
                .form("query", query)
                .form("per_page", searchCount)
                .form("page", 1)
                .timeout(10000)
                .execute()) {
            if (!response.isOk()) {
                log.warn("Pexels 搜索失败，状态码: {}", response.getStatus());
                return imageList;
            }
            JSONObject result = JSONUtil.parseObj(response.body());
            JSONArray photos = result.getJSONArray("photos");
            if (photos == null) {
                return imageList;
            }
            for (int i = 0; i < photos.size(); i++) {
                JSONObject photo = photos.getJSONObject(i);
                JSONObject src = photo.getJSONObject("src");
                if (src == null || StrUtil.isBlank(src.getStr("medium"))) {
                    continue;
                }
                imageList.add(ImageResource.builder()
                        .category(ImageCategoryEnum.CONTENT)
                        .description(StrUtil.blankToDefault(photo.getStr("alt"), query))
                        .url(src.getStr("medium"))
                        .build());
            }
        } catch (Exception e) {
            log.error("Pexels API 调用失败: {}", e.getMessage(), e);
        }
        return imageList;
    }
}
