package com.leo.aigenweb.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 项目下载服务
 */
public interface ProjectDownloadService {

    /**
     * 将指定目录打包为 ZIP 并写入响应
     *
     * @param projectPath      项目根目录
     * @param downloadFileName 下载文件名（不含扩展名）
     * @param response         HTTP 响应
     */
    void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);
}
