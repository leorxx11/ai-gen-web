package com.leo.aigenweb.langgraph4j.tools;

import com.leo.aigenweb.config.DarkroomConfig;
import com.leo.aigenweb.langgraph4j.model.ImageResource;
import com.leo.aigenweb.langgraph4j.model.enums.ImageCategoryEnum;
import com.leo.aigenweb.manager.DarkroomManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 图片收集工具测试：外部服务（Pexels / unDraw / DashScope / 图床）全部用本地假服务器替代，不依赖外网
 */
class ImageToolsTest {

    private HttpServer server;
    private String base;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void reply(HttpExchange ex, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private DarkroomManager darkroomManagerReturning(String url) {
        server.createContext("/api/v1/upload", ex -> {
            ex.getRequestBody().readAllBytes();
            reply(ex, 200, "{\"image\":{\"url\":\"" + url + "\"}}");
        });
        DarkroomConfig config = new DarkroomConfig();
        config.setBaseUrl(base);
        config.setToken("t");
        DarkroomManager manager = new DarkroomManager();
        ReflectionTestUtils.setField(manager, "darkroomConfig", config);
        return manager;
    }

    // ---------- Pexels ----------

    @Test
    void imageSearchParsesPhotosAndSendsKey() {
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        server.createContext("/v1/search", ex -> {
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            query.set(ex.getRequestURI().getQuery());
            reply(ex, 200, "{\"photos\":[{\"alt\":\"a cat\",\"src\":{\"medium\":\"https://img/1.jpg\"}},"
                    + "{\"alt\":\"\",\"src\":{\"medium\":\"https://img/2.jpg\"}},{\"src\":{}}]}");
        });
        ImageSearchTool tool = new ImageSearchTool();
        ReflectionTestUtils.setField(tool, "pexelsBaseUrl", base);
        ReflectionTestUtils.setField(tool, "pexelsApiKey", "key-123");

        List<ImageResource> images = tool.searchContentImages("cat");

        assertEquals(2, images.size());
        assertEquals(ImageCategoryEnum.CONTENT, images.get(0).getCategory());
        assertEquals("a cat", images.get(0).getDescription());
        assertEquals("cat", images.get(1).getDescription(), "alt 为空时回退为搜索词");
        assertEquals("key-123", auth.get());
        assertTrue(query.get().contains("query=cat"));
    }

    @Test
    void imageSearchReturnsEmptyOnErrorOrMissingKey() {
        server.createContext("/v1/search", ex -> reply(ex, 401, "{}"));
        ImageSearchTool tool = new ImageSearchTool();
        ReflectionTestUtils.setField(tool, "pexelsBaseUrl", base);
        ReflectionTestUtils.setField(tool, "pexelsApiKey", "bad");
        assertTrue(tool.searchContentImages("cat").isEmpty());
        ReflectionTestUtils.setField(tool, "pexelsApiKey", "");
        assertTrue(tool.searchContentImages("cat").isEmpty());
        assertTrue(tool.searchContentImages(" ").isEmpty());
    }

    // ---------- unDraw ----------

    @Test
    void undrawUsesBuildIdParsedFromSearchPage() {
        AtomicReference<String> dataPath = new AtomicReference<>();
        server.createContext("/search", ex -> reply(ex, 200, "<script>{\"buildId\":\"LIVE_BUILD\"}</script>"));
        server.createContext("/_next/data", ex -> {
            dataPath.set(ex.getRequestURI().getPath());
            reply(ex, 200, "{\"pageProps\":{\"initialResults\":[{\"title\":\"Happy\",\"media\":\"https://undraw/a.svg\"},"
                    + "{\"title\":\"NoMedia\",\"media\":\"\"}]}}");
        });
        UndrawIllustrationTool tool = new UndrawIllustrationTool();
        ReflectionTestUtils.setField(tool, "undrawBaseUrl", base);

        List<ImageResource> images = tool.searchIllustrations("happy");

        assertEquals(1, images.size());
        assertEquals(ImageCategoryEnum.ILLUSTRATION, images.get(0).getCategory());
        assertEquals("/_next/data/LIVE_BUILD/search/happy.json", dataPath.get());
    }

    @Test
    void undrawFallsBackToDefaultBuildIdAndHandlesFailure() {
        AtomicReference<String> dataPath = new AtomicReference<>();
        server.createContext("/search", ex -> reply(ex, 500, "boom"));
        server.createContext("/_next/data", ex -> {
            dataPath.set(ex.getRequestURI().getPath());
            reply(ex, 404, "{}");
        });
        UndrawIllustrationTool tool = new UndrawIllustrationTool();
        ReflectionTestUtils.setField(tool, "undrawBaseUrl", base);
        assertTrue(tool.searchIllustrations("happy").isEmpty());
        assertTrue(dataPath.get().startsWith("/_next/data/mMWmJSt23qpgo8cLTD_pB/"), dataPath.get());
    }

    // ---------- DashScope Logo ----------

    @Test
    void logoGeneratorSubmitsPollsDownloadsAndUploads() {
        AtomicInteger polls = new AtomicInteger();
        AtomicReference<String> submitAuth = new AtomicReference<>();
        AtomicReference<String> submitBody = new AtomicReference<>();
        server.createContext("/api/v1/services/aigc/text2image/image-synthesis", ex -> {
            submitAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            submitBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(ex, 200, "{\"output\":{\"task_id\":\"task-1\",\"task_status\":\"PENDING\"}}");
        });
        server.createContext("/api/v1/tasks/task-1", ex -> {
            if (polls.incrementAndGet() < 2) {
                reply(ex, 200, "{\"output\":{\"task_status\":\"RUNNING\"}}");
            } else {
                reply(ex, 200, "{\"output\":{\"task_status\":\"SUCCEEDED\",\"results\":[{\"url\":\"" + base + "/img/logo.png\"}]}}");
            }
        });
        server.createContext("/img/logo.png", ex -> reply(ex, 200, "PNGDATA"));
        LogoGeneratorTool tool = new LogoGeneratorTool();
        ReflectionTestUtils.setField(tool, "baseUrl", base);
        ReflectionTestUtils.setField(tool, "dashScopeApiKey", "dk");
        ReflectionTestUtils.setField(tool, "imageModel", "wan-test");
        ReflectionTestUtils.setField(tool, "pollMaxAttempts", 5);
        ReflectionTestUtils.setField(tool, "pollIntervalMs", 10L);
        ReflectionTestUtils.setField(tool, "darkroomManager", darkroomManagerReturning("https://img.mine/logo.png"));

        List<ImageResource> logos = tool.generateLogos("一家咖啡店");

        assertEquals(1, logos.size());
        assertEquals(ImageCategoryEnum.LOGO, logos.get(0).getCategory());
        assertEquals("https://img.mine/logo.png", logos.get(0).getUrl(), "应使用转存后的图床地址，而不是 24 小时过期的原链接");
        assertEquals("Bearer dk", submitAuth.get());
        assertTrue(submitBody.get().contains("wan-test") && submitBody.get().contains("一家咖啡店"));
        assertEquals(2, polls.get());
    }

    @Test
    void logoGeneratorReturnsEmptyWhenTaskFailsOrKeyMissing() {
        server.createContext("/api/v1/services/aigc/text2image/image-synthesis",
                ex -> reply(ex, 200, "{\"output\":{\"task_id\":\"t2\"}}"));
        server.createContext("/api/v1/tasks/t2", ex -> reply(ex, 200, "{\"output\":{\"task_status\":\"FAILED\"}}"));
        LogoGeneratorTool tool = new LogoGeneratorTool();
        ReflectionTestUtils.setField(tool, "baseUrl", base);
        ReflectionTestUtils.setField(tool, "dashScopeApiKey", "dk");
        ReflectionTestUtils.setField(tool, "imageModel", "m");
        ReflectionTestUtils.setField(tool, "pollMaxAttempts", 3);
        ReflectionTestUtils.setField(tool, "pollIntervalMs", 1L);
        ReflectionTestUtils.setField(tool, "darkroomManager", darkroomManagerReturning("x"));
        assertTrue(tool.generateLogos("logo").isEmpty());
        ReflectionTestUtils.setField(tool, "dashScopeApiKey", "");
        assertTrue(tool.generateLogos("logo").isEmpty());
    }

    // ---------- Mermaid ----------

    @Test
    void mermaidRendersPngAndUploads() {
        String chrome = System.getenv("SCREENSHOT_CHROME_BINARY");
        Assumptions.assumeTrue(chrome != null && new File(chrome).exists() && isMmdcAvailable(),
                "未安装 mermaid-cli 或未设置 SCREENSHOT_CHROME_BINARY，跳过架构图渲染测试");
        MermaidDiagramTool tool = new MermaidDiagramTool();
        ReflectionTestUtils.setField(tool, "mermaidCliPath", "mmdc");
        ReflectionTestUtils.setField(tool, "chromePath", chrome);
        ReflectionTestUtils.setField(tool, "darkroomManager", darkroomManagerReturning("https://img.mine/diagram.png"));

        List<ImageResource> diagrams = tool.generateMermaidDiagram("flowchart LR\n A[开始] --> B[结束]", "流程图");

        assertEquals(1, diagrams.size());
        assertEquals(ImageCategoryEnum.ARCHITECTURE, diagrams.get(0).getCategory());
        assertEquals("流程图", diagrams.get(0).getDescription());
        assertEquals("https://img.mine/diagram.png", diagrams.get(0).getUrl());
    }

    @Test
    void mermaidReturnsEmptyForBlankOrInvalidCode() {
        MermaidDiagramTool tool = new MermaidDiagramTool();
        ReflectionTestUtils.setField(tool, "mermaidCliPath", "definitely-not-installed-mmdc");
        ReflectionTestUtils.setField(tool, "chromePath", "");
        ReflectionTestUtils.setField(tool, "darkroomManager", darkroomManagerReturning("x"));
        assertTrue(tool.generateMermaidDiagram("  ", "d").isEmpty());
        // 命令不存在时不应抛异常
        assertTrue(tool.generateMermaidDiagram("flowchart LR\n A-->B", "d").isEmpty());
    }

    private boolean isMmdcAvailable() {
        try {
            Process p = new ProcessBuilder("mmdc", "--version").redirectErrorStream(true).start();
            return p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
