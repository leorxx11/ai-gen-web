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
 * 工作流端到端测试：真实调用 LLM。图床上传用 mock 替代，避免测试产生垃圾图片。
 * 图片搜索等外部服务未配置或网络不通时会自动降级为没有图片，不影响断言。
 */
@Slf4j
@SpringBootTest
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

    @Test
    void concurrentWorkflow() {
        when(darkroomManager.uploadFile(any(File.class))).thenReturn("https://img.example.com/fake.png");
        WorkflowContext result = new CodeGenConcurrentWorkflow().executeWorkflow("创建一个简单的个人主页");
        generatedDir = result.getGeneratedCodeDir();
        assertNotNull(result.getImageCollectionPlan(), "应当由 AI 生成了图片收集计划");
        assertNotNull(result.getImageList());
        assertTrue(new File(result.getGeneratedCodeDir()).isDirectory());
    }
}
