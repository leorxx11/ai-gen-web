package com.leo.aigenweb.langgraph4j;

import cn.hutool.core.io.FileUtil;
import com.leo.aigenweb.langgraph4j.state.WorkflowContext;
import com.leo.aigenweb.manager.DarkroomManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 工作流端到端测试（关闭图片收集）：真实调用 LLM。图床上传用 mock 替代，避免测试产生垃圾图片。
 */
@Slf4j
@SpringBootTest(properties = "workflow.image-collection.enabled=false")
class CodeGenWorkflowTest {

    @MockitoBean
    private DarkroomManager darkroomManager;

    private String generatedDir;

    @AfterEach
    void cleanup() {
        if (generatedDir != null) {
            FileUtil.del(new File(generatedDir));
        }
    }

    @Test
    void simpleHtmlWorkflow() {
        when(darkroomManager.uploadFile(any(File.class))).thenReturn("https://img.example.com/fake.png");
        WorkflowContext result = new CodeGenWorkflow().executeWorkflow("创建一个简单的个人主页");
        generatedDir = result.getGeneratedCodeDir();
        log.info("生成类型: {}, 目录: {}, 图片数: {}, 质检: {}", result.getGenerationType(), result.getGeneratedCodeDir(),
                result.getImageList() == null ? 0 : result.getImageList().size(), result.getQualityResult());
        assertNotNull(result.getGenerationType());
        assertNotNull(result.getEnhancedPrompt());
        assertNotNull(result.getQualityResult());
        assertTrue(new File(result.getGeneratedCodeDir()).isDirectory(), "应当生成了代码目录");
        assertTrue(FileUtil.loopFiles(new File(result.getGeneratedCodeDir())).size() > 0, "目录下应当有代码文件");
    }
}
