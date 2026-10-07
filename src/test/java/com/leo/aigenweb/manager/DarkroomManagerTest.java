package com.leo.aigenweb.manager;

import cn.hutool.core.io.FileUtil;
import com.leo.aigenweb.config.DarkroomConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DarkroomManagerTest {

    private HttpServer server;
    private final DarkroomManager manager = new DarkroomManager();
    private final AtomicReference<String> authHeader = new AtomicReference<>();
    private final AtomicReference<byte[]> receivedBody = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody = "{\"image\":{\"url\":\"https://img.example.com/i/a.jpg\"}}";

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/upload", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            receivedBody.set(exchange.getRequestBody().readAllBytes());
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        DarkroomConfig config = new DarkroomConfig();
        config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        config.setToken("test-token");
        ReflectionTestUtils.setField(manager, "darkroomConfig", config);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void uploadFileReturnsUrlAndSendsBearerToken(@TempDir File dir) {
        File file = new File(dir, "cover.jpg");
        byte[] content = {1, 2, 3, 4, 5};
        FileUtil.writeBytes(content, file);
        assertEquals("https://img.example.com/i/a.jpg", manager.uploadFile(file));
        assertEquals("Bearer test-token", authHeader.get());
        assertArrayEquals(content, receivedBody.get());
    }

    @Test
    void uploadFileReturnsNullWhenServerRejects(@TempDir File dir) {
        status = 401;
        responseBody = "{\"error\":\"invalid token\"}";
        File file = new File(dir, "cover.jpg");
        FileUtil.writeBytes(new byte[]{1}, file);
        assertNull(manager.uploadFile(file));
    }

    @Test
    void uploadFileReturnsNullWhenFileMissing() {
        assertNull(manager.uploadFile(new File("/no/such/file.jpg")));
    }
}
