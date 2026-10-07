package com.leo.aigenweb.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.leo.aigenweb.exception.BusinessException;
import com.leo.aigenweb.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;
import java.util.UUID;

/**
 * 网页截图工具类
 * <p>
 * 每次截图都创建独立的 WebDriver 并在用完后关闭，避免并发截图时多个线程共用同一个浏览器而截错页面。
 * 如需指定本机 Chrome / chromedriver，可设置环境变量 SCREENSHOT_CHROME_BINARY / SCREENSHOT_CHROMEDRIVER_PATH。
 */
@Slf4j
public class WebScreenshotUtils {

    private static final int DEFAULT_WIDTH = 1600;
    private static final int DEFAULT_HEIGHT = 900;

    /**
     * 截图临时目录
     */
    private static final String SCREENSHOT_TEMP_ROOT =
            System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "screenshots";

    private WebScreenshotUtils() {
    }

    /**
     * 初始化 Chrome 浏览器驱动
     */
    private static WebDriver initChromeDriver(int width, int height) {
        try {
            String chromeBinary = System.getenv("SCREENSHOT_CHROME_BINARY");
            String chromedriverPath = System.getenv("SCREENSHOT_CHROMEDRIVER_PATH");
            // 配置 Chrome 选项
            ChromeOptions options = new ChromeOptions();
            if (StrUtil.isNotBlank(chromeBinary)) {
                options.setBinary(chromeBinary);
            }
            // 无头模式
            options.addArguments("--headless=new");
            // 禁用 GPU（在某些环境下避免问题）
            options.addArguments("--disable-gpu");
            // 禁用沙盒模式（Docker 环境需要）
            options.addArguments("--no-sandbox");
            // 禁用 /dev/shm 使用
            options.addArguments("--disable-dev-shm-usage");
            // 设置窗口大小
            options.addArguments(String.format("--window-size=%d,%d", width, height));
            // 禁用扩展
            options.addArguments("--disable-extensions");
            // 设置用户代理
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            WebDriver driver;
            if (StrUtil.isNotBlank(chromedriverPath)) {
                // 使用指定的 chromedriver
                driver = new ChromeDriver(new ChromeDriverService.Builder()
                        .usingDriverExecutable(new File(chromedriverPath)).build(), options);
            } else {
                // 未指定时由 Selenium Manager（Selenium 4.6+ 内置）自动匹配并下载 ChromeDriver
                driver = new ChromeDriver(options);
            }
            // 设置页面加载超时
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            // 设置隐式等待
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            return driver;
        } catch (Exception e) {
            log.error("初始化 Chrome 浏览器失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化 Chrome 浏览器失败");
        }
    }

    /**
     * 保存图片到文件
     */
    private static void saveImage(byte[] imageBytes, String imagePath) {
        try {
            FileUtil.writeBytes(imageBytes, imagePath);
        } catch (Exception e) {
            log.error("保存图片失败: {}", imagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存图片失败");
        }
    }

    /**
     * 压缩图片
     */
    private static void compressImage(String originalImagePath, String compressedImagePath) {
        // 压缩图片质量（0.1 = 10% 质量）
        final float compressionQuality = 0.3f;
        try {
            ImgUtil.compress(
                    FileUtil.file(originalImagePath),
                    FileUtil.file(compressedImagePath),
                    compressionQuality
            );
        } catch (Exception e) {
            log.error("压缩图片失败: {} -> {}", originalImagePath, compressedImagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩图片失败");
        }
    }

    /**
     * 等待页面加载完成
     */
    private static void waitForPageLoad(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            // 等待 document.readyState 为 complete
            wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
            // 额外等待一段时间，确保动态内容渲染完成
            Thread.sleep(2000);
            log.info("页面加载完成");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("等待页面加载被中断，继续执行截图", e);
        } catch (Exception e) {
            log.error("等待页面加载时出现异常，继续执行截图", e);
        }
    }

    /**
     * 生成网页截图
     *
     * @param webUrl 网页 URL
     * @return 压缩后的截图文件路径，失败返回 null
     */
    public static String saveWebPageScreenshot(String webUrl) {
        if (StrUtil.isBlank(webUrl)) {
            log.error("网页URL不能为空");
            return null;
        }
        WebDriver driver = null;
        try {
            driver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);
            // 创建临时目录
            String rootPath = SCREENSHOT_TEMP_ROOT + File.separator + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);
            // 原始截图文件路径
            String imageSavePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + ".png";
            // 访问网页并等待加载完成
            driver.get(webUrl);
            waitForPageLoad(driver);
            // 截图并保存原始图片
            byte[] screenshotBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            saveImage(screenshotBytes, imageSavePath);
            log.info("原始截图保存成功: {}", imageSavePath);
            // 压缩图片
            String compressedImagePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + "_compressed.jpg";
            compressImage(imageSavePath, compressedImagePath);
            log.info("压缩图片保存成功: {}", compressedImagePath);
            // 删除原始图片，只保留压缩图片
            FileUtil.del(imageSavePath);
            return compressedImagePath;
        } catch (Exception e) {
            log.error("网页截图失败: {}", webUrl, e);
            return null;
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    log.warn("关闭浏览器失败", e);
                }
            }
        }
    }

    /**
     * 清理临时截图目录
     */
    public static void cleanupTempFiles() {
        FileUtil.clean(SCREENSHOT_TEMP_ROOT);
    }
}
