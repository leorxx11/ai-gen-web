package com.leo.aigenweb.langgraph4j;

import com.leo.aigenweb.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

/**
 * 工作流开关配置。工作流不是 Spring Bean（图是代码里 new 出来的），所以通过 Spring 环境读取配置。
 */
@Slf4j
public class WorkflowSettings {

    private static final String IMAGE_COLLECTION_ENABLED = "workflow.image-collection.enabled";

    private WorkflowSettings() {
    }

    /**
     * 是否启用图片收集步骤，默认关闭
     */
    public static boolean isImageCollectionEnabled() {
        try {
            return SpringContextUtil.getBean(Environment.class)
                    .getProperty(IMAGE_COLLECTION_ENABLED, Boolean.class, false);
        } catch (Exception e) {
            log.warn("读取工作流配置失败，按默认值处理（图片收集关闭）: {}", e.getMessage());
            return false;
        }
    }
}
