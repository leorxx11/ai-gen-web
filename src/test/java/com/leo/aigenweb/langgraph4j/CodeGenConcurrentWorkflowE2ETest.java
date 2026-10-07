package com.leo.aigenweb.langgraph4j;

import cn.hutool.core.io.FileUtil;
import com.leo.aigenweb.langgraph4j.state.WorkflowContext;
import com.leo.aigenweb.manager.DarkroomManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 开启图片收集的并发工作流端到端测试：真实调用 LLM，图床上传用 mock 替代。
 * 图片搜索等外部服务未配置或网络不通时会降级为没有图片，不影响断言。
 */
@SpringBootTest(properties = "workflow.image-collection.enabled=true")
class CodeGenConcurrentWorkflowE2ETest {

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
    void concurrentWorkflowWithImageCollection() {
        when(darkroomManager.uploadFile(any(File.class))).thenReturn("https://img.example.com/fake.png");
        WorkflowContext result = new CodeGenConcurrentWorkflow().executeWorkflow("创建一个简单的个人主页");
        generatedDir = result.getGeneratedCodeDir();
        assertNotNull(result.getImageCollectionPlan(), "应当由 AI 生成了图片收集计划");
        assertNotNull(result.getImageList());
        assertTrue(new File(result.getGeneratedCodeDir()).isDirectory());
    }
}
