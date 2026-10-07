package com.leo.aigenweb.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.leo.aigenweb.exception.ErrorCode;
import com.leo.aigenweb.exception.ThrowUtils;
import com.leo.aigenweb.manager.DarkroomManager;
import com.leo.aigenweb.service.ScreenshotService;
import com.leo.aigenweb.utils.WebScreenshotUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * 截图服务实现：只负责“网址 -> 截图地址”，不包含 appId 等具体业务参数
 */
@Service
@Slf4j
public class ScreenshotServiceImpl implements ScreenshotService {

    @Resource
    private DarkroomManager darkroomManager;

    @Override
    public String generateAndUploadScreenshot(String webUrl) {
        ThrowUtils.throwIf(StrUtil.isBlank(webUrl), ErrorCode.PARAMS_ERROR, "网页URL不能为空");
        log.info("开始生成网页截图，URL: {}", webUrl);
        // 1. 生成本地截图
        String localScreenshotPath = WebScreenshotUtils.saveWebPageScreenshot(webUrl);
        ThrowUtils.throwIf(StrUtil.isBlank(localScreenshotPath), ErrorCode.OPERATION_ERROR, "本地截图生成失败");
        try {
            // 2. 上传到图床
            String imageUrl = darkroomManager.uploadFile(new File(localScreenshotPath));
            ThrowUtils.throwIf(StrUtil.isBlank(imageUrl), ErrorCode.OPERATION_ERROR, "截图上传图床失败");
            log.info("网页截图生成并上传成功: {} -> {}", webUrl, imageUrl);
            return imageUrl;
        } finally {
            // 3. 上传后立即清理本地文件，避免占用磁盘
            cleanupLocalFile(localScreenshotPath);
        }
    }

    /**
     * 清理本地截图文件（连同它所在的临时目录）
     */
    private void cleanupLocalFile(String localFilePath) {
        File localFile = new File(localFilePath);
        if (localFile.exists()) {
            FileUtil.del(localFile.getParentFile());
            log.info("本地截图文件已清理: {}", localFilePath);
        }
    }
}
