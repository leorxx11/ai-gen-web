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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 内容图片搜索工具：通过 Pixabay 免费图库按关键词搜索图片。
 * <p>
 * Pixabay 不允许长期直接引用（热链）其图片地址，因此搜到的图片会先下载，再转存到自己的图床，
 * 返回的是图床地址。Pixabay 的密钥只能放在 URL 查询参数里（无法由网络代理注入请求头），
 * 请通过环境变量 PIXABAY_API_KEY 提供，并注意不要把含密钥的 URL 打进日志。
 */
@Slf4j
@Component
public class ImageSearchTool {

    /**
     * 每次搜索最多转存的图片数量，控制耗时和提示词长度
     */
    private static final int MAX_IMAGES_PER_QUERY = 4;

    @Value("${pixabay.base-url:https://pixabay.com}")
    private String pixabayBaseUrl;

    @Value("${pixabay.api-key:}")
    private String pixabayApiKey;

    @Resource
    private DarkroomManager darkroomManager;

    @Tool("搜索内容相关的图片，用于网站内容展示")
    public List<ImageResource> searchContentImages(@P("搜索关键词") String query) {
        List<ImageResource> imageList = new ArrayList<>();
        if (StrUtil.hasBlank(query, pixabayApiKey)) {
            log.warn("Pixabay 未配置 api-key 或关键词为空，跳过内容图片搜索");
            return imageList;
        }
        try {
            JSONArray hits = search(query);
            if (hits == null || hits.isEmpty()) {
                return imageList;
            }
            int count = Math.min(MAX_IMAGES_PER_QUERY, hits.size());
            // 下载并转存都是 IO 操作，并发进行
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<CompletableFuture<ImageResource>> futures = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    JSONObject hit = hits.getJSONObject(i);
                    futures.add(CompletableFuture.supplyAsync(() -> rehost(hit, query), executor)
                            .exceptionally(e -> {
                                log.warn("转存图片失败: {}", mask(e.getMessage()));
                                return null;
                            }));
                }
                for (CompletableFuture<ImageResource> future : futures) {
                    ImageResource image = future.join();
                    if (image != null) {
                        imageList.add(image);
                    }
                }
            }
        } catch (Exception e) {
            // 异常信息里可能带有含密钥的 URL，脱敏后再记录
            log.error("Pixabay 图片搜索失败: {}", mask(e.getMessage()));
        }
        return imageList;
    }

    /**
     * 调用 Pixabay 搜索接口，返回 hits 数组
     */
    private JSONArray search(String query) {
        try (HttpResponse response = HttpRequest.get(pixabayBaseUrl + "/api/")
                .form("key", pixabayApiKey)
                .form("q", query)
                .form("image_type", "photo")
                .form("orientation", "horizontal")
                .form("safesearch", "true")
                // Pixabay 要求 per_page 不小于 3
                .form("per_page", 8)
                .timeout(10000)
                .execute()) {
            if (!response.isOk()) {
                log.warn("Pixabay 搜索失败，状态码: {}, 响应: {}", response.getStatus(), mask(response.body()));
                return null;
            }
            return JSONUtil.parseObj(response.body()).getJSONArray("hits");
        }
    }

    /**
     * 下载一张搜索结果并转存到图床，失败返回 null
     */
    private ImageResource rehost(JSONObject hit, String query) {
        String sourceUrl = hit.getStr("webformatURL");
        if (StrUtil.isBlank(sourceUrl)) {
            return null;
        }
        File file = FileUtil.createTempFile("pixabay_", ".jpg", true);
        try {
            long size = HttpRequest.get(sourceUrl).timeout(20000).executeAsync().writeBody(file);
            if (size <= 0) {
                return null;
            }
            String url = darkroomManager.uploadFile(file);
            if (StrUtil.isBlank(url)) {
                return null;
            }
            return ImageResource.builder()
                    .category(ImageCategoryEnum.CONTENT)
                    .description(StrUtil.blankToDefault(hit.getStr("tags"), query))
                    .url(url)
                    .build();
        } finally {
            FileUtil.del(file);
        }
    }

    /**
     * 把文本中的密钥替换掉，避免写进日志
     */
    private String mask(String text) {
        if (text == null || StrUtil.isBlank(pixabayApiKey)) {
            return text;
        }
        return text.replace(pixabayApiKey, "***");
    }
}
