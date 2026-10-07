package com.leo.aigenweb.service;

/**
 * 截图服务
 */
public interface ScreenshotService {

    /**
     * 生成网页截图并上传到图床
     *
     * @param webUrl 网页 URL
     * @return 截图的访问地址
     */
    String generateAndUploadScreenshot(String webUrl);
}
