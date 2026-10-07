package com.leo.aigenweb.langgraph4j;

import cn.hutool.core.io.FileUtil;
import com.leo.aigenweb.ai.AiCodeGenTypeRoutingService;
import com.leo.aigenweb.constant.AppConstant;
import com.leo.aigenweb.core.AiCodeGeneratorFacade;
import com.leo.aigenweb.langgraph4j.ai.CodeQualityCheckService;
import com.leo.aigenweb.langgraph4j.ai.ImageCollectionPlanService;
import com.leo.aigenweb.langgraph4j.model.QualityResult;
import com.leo.aigenweb.langgraph4j.state.WorkflowContext;
import com.leo.aigenweb.langgraph4j.tools.ImageSearchTool;
import com.leo.aigenweb.langgraph4j.tools.LogoGeneratorTool;
import com.leo.aigenweb.langgraph4j.tools.MermaidDiagramTool;
import com.leo.aigenweb.langgraph4j.tools.UndrawIllustrationTool;
import com.leo.aigenweb.model.enums.CodeGenTypeEnum;
import org.bsc.langgraph4j.GraphRepresentation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 图片收集关闭时（workflow.image-collection.enabled=false）：工作流不应包含图片收集节点，也不应调用图片规划 AI 和任何图片工具
 */
@SpringBootTest(properties = "workflow.image-collection.enabled=false")
class CodeGenWorkflowImageDisabledTest {

    @MockitoBean
    private AiCodeGeneratorFacade facade;
    @MockitoBean
    private ImageCollectionPlanService planService;
    @MockitoBean
    private AiCodeGenTypeRoutingService routingService;
    @MockitoBean
    private CodeQualityCheckService qualityService;
    @MockitoBean
    private ImageSearchTool imageSearchTool;
    @MockitoBean
    private UndrawIllustrationTool undrawTool;
    @MockitoBean
    private MermaidDiagramTool mermaidTool;
    @MockitoBean
    private LogoGeneratorTool logoTool;

    private final List<File> createdDirs = new CopyOnWriteArrayList<>();

    @BeforeEach
    void stub() {
        when(routingService.routeCodeGenType(anyString())).thenReturn(CodeGenTypeEnum.HTML);
        when(qualityService.checkCodeQuality(anyString())).thenReturn(QualityResult.builder().isValid(true).build());
        when(facade.generateAndSaveCodeStream(anyString(), any(CodeGenTypeEnum.class), anyLong())).thenAnswer(inv -> {
            CodeGenTypeEnum type = inv.getArgument(1);
            Long appId = inv.getArgument(2);
            File dir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR, type.getValue() + "_" + appId);
            FileUtil.writeUtf8String("<html><body>ok</body></html>", new File(dir, "index.html"));
            createdDirs.add(dir);
            return Flux.just("generated");
        });
    }

    @AfterEach
    void cleanup() {
        createdDirs.forEach(FileUtil::del);
    }

    @Test
    void graphHasNoImageNodes() {
        String mermaid = new CodeGenWorkflow().createWorkflow().getGraph(GraphRepresentation.Type.MERMAID).content();
        assertTrue(mermaid.contains(WorkflowNodeNames.PROMPT_ENHANCER) && mermaid.contains(WorkflowNodeNames.CODE_GENERATOR), mermaid);
        for (String imageNode : List.of(WorkflowNodeNames.IMAGE_COLLECTOR, WorkflowNodeNames.IMAGE_PLAN,
                WorkflowNodeNames.IMAGE_AGGREGATOR, WorkflowNodeNames.LOGO_COLLECTOR)) {
            assertFalse(mermaid.contains(imageNode), "关闭时不应包含节点 " + imageNode);
        }
        // 并发版在关闭时同样不含图片节点
        String concurrent = new CodeGenConcurrentWorkflow().createWorkflow().getGraph(GraphRepresentation.Type.MERMAID).content();
        assertFalse(concurrent.contains(WorkflowNodeNames.IMAGE_PLAN), concurrent);
    }

    @Test
    void serialAndConcurrentWorkflowsRunWithoutTouchingImageServices() {
        for (AbstractCodeGenWorkflow workflow : List.of(new CodeGenWorkflow(), new CodeGenConcurrentWorkflow())) {
            WorkflowContext result = workflow.executeWorkflow("做一个咖啡店网站");
            assertEquals(CodeGenTypeEnum.HTML, result.getGenerationType());
            assertNull(result.getImageList());
            // 增强提示词没有素材段落，就是原始提示词
            assertEquals("做一个咖啡店网站", result.getEnhancedPrompt());
        }
        verifyNoInteractions(planService, imageSearchTool, undrawTool, mermaidTool, logoTool);
        verify(facade, times(2)).generateAndSaveCodeStream(eq("做一个咖啡店网站"), eq(CodeGenTypeEnum.HTML), anyLong());
    }
}
