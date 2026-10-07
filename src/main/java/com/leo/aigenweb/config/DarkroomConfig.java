package com.leo.aigenweb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 暗房图床配置。令牌请通过环境变量 DARKROOM_TOKEN 注入，不要写进代码或提交到仓库
 */
@Configuration
@ConfigurationProperties(prefix = "darkroom")
@Data
public class DarkroomConfig {

    /**
     * 图床服务地址
     */
    private String baseUrl = "https://leorxx.xyz";

    /**
     * 上传令牌
     */
    private String token;

    /**
     * 请求超时（毫秒）
     */
    private int timeoutMs = 30000;
}
