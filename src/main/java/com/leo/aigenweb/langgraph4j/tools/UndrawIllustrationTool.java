package com.leo.aigenweb.langgraph4j.tools;

import cn.hutool.core.util.ReUtil;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 插画图片搜索工具：通过 unDraw 站点的搜索接口获取免费插画
 * <p>
 * 该接口来自 Next.js 站点的数据路由，路径里带有每次发版都会变化的 buildId，
 * 因此每次都从搜索页动态解析 buildId，解析失败再使用兜底值。
 */
@Slf4j
@Component
public class UndrawIllustrationTool {

    /**
     * 兜底 buildId（站点发版后会失效，仅在动态解析失败时使用）
     */
    private static final String FALLBACK_BUILD_ID = "mMWmJSt23qpgo8cLTD_pB";

    @Value("${undraw.base-url:https://undraw.co}")
    private String undrawBaseUrl;

    @Tool("搜索插画图片，用于网站美化和装饰")
    public List<ImageResource> searchIllustrations(@P("搜索关键词") String query) {
        List<ImageResource> imageList = new ArrayList<>();
        if (StrUtil.isBlank(query)) {
            return imageList;
        }
        int searchCount = 12;
        String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8).replace("+", "%20");
        String apiUrl = String.format("%s/_next/data/%s/search/%s.json?term=%s",
                undrawBaseUrl, resolveBuildId(), encoded, encoded);
        // 使用 try-with-resources 自动释放 HTTP 资源
        try (HttpResponse response = HttpRequest.get(apiUrl).timeout(10000).execute()) {
            if (!response.isOk()) {
                log.warn("unDraw 搜索失败，状态码: {}", response.getStatus());
                return imageList;
            }
            JSONObject pageProps = JSONUtil.parseObj(response.body()).getJSONObject("pageProps");
            if (pageProps == null) {
                return imageList;
            }
            JSONArray initialResults = pageProps.getJSONArray("initialResults");
            if (initialResults == null || initialResults.isEmpty()) {
                return imageList;
            }
            int actualCount = Math.min(searchCount, initialResults.size());
            for (int i = 0; i < actualCount; i++) {
                JSONObject illustration = initialResults.getJSONObject(i);
                String title = illustration.getStr("title", "插画");
                String media = illustration.getStr("media", "");
                if (StrUtil.isNotBlank(media)) {
                    imageList.add(ImageResource.builder()
                            .category(ImageCategoryEnum.ILLUSTRATION)
                            .description(title)
                            .url(media)
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("搜索插画失败：{}", e.getMessage(), e);
        }
        return imageList;
    }

    /**
     * 从搜索页解析 Next.js 的 buildId，失败返回兜底值
     */
    private String resolveBuildId() {
        try (HttpResponse response = HttpRequest.get(undrawBaseUrl + "/search").timeout(10000).execute()) {
            if (response.isOk()) {
                String buildId = ReUtil.getGroup1("\"buildId\"\\s*:\\s*\"([^\"]+)\"", response.body());
                if (StrUtil.isNotBlank(buildId)) {
                    return buildId;
                }
            }
        } catch (Exception e) {
            log.warn("解析 unDraw buildId 失败，使用兜底值: {}", e.getMessage());
        }
        return FALLBACK_BUILD_ID;
    }
}
