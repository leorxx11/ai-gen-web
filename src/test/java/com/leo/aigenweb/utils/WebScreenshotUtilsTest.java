package com.leo.aigenweb.utils;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
class WebScreenshotUtilsTest {

    @Test
    void saveWebPageScreenshot(@TempDir File dir) {
        // 本机没有 Chrome 时跳过：设置 SCREENSHOT_CHROME_BINARY（及匹配的 SCREENSHOT_CHROMEDRIVER_PATH）后才会执行
        Assumptions.assumeTrue(System.getenv("SCREENSHOT_CHROME_BINARY") != null
                        || System.getenv("CI_HAS_CHROME") != null
                        || new File("/usr/bin/google-chrome").exists(),
                "未检测到 Chrome，跳过截图测试");
        File page = new File(dir, "index.html");
        FileUtil.writeUtf8String("<html><body style=\"background:#4f46e5\"><h1 style=\"color:#fff\">截图测试</h1></body></html>", page);
        String path = WebScreenshotUtils.saveWebPageScreenshot(page.toURI().toString());
        log.info("截图路径: {}", path);
        assertNotNull(path);
        File out = new File(path);
        assertTrue(out.exists() && out.length() > 0);
        assertTrue(path.endsWith(".jpg"));
        FileUtil.del(out.getParentFile());
    }
}
