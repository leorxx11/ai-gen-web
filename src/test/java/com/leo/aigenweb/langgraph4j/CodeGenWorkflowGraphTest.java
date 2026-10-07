package com.leo.aigenweb.langgraph4j;

import cn.hutool.core.io.FileUtil;
import com.leo.aigenweb.ai.AiCodeGenTypeRoutingService;
import com.leo.aigenweb.ai.AiCodeGenTypeRoutingServiceFactory;
import com.leo.aigenweb.constant.AppConstant;
import com.leo.aigenweb.core.AiCodeGeneratorFacade;
import com.leo.aigenweb.core.builder.VueProjectBuilder;
import com.leo.aigenweb.langgraph4j.ai.CodeQualityCheckService;
import com.leo.aigenweb.langgraph4j.ai.ImageCollectionPlanService;
import com.leo.aigenweb.langgraph4j.model.ImageCollectionPlan;
import com.leo.aigenweb.langgraph4j.model.ImageResource;
import com.leo.aigenweb.langgraph4j.model.QualityResult;
import com.leo.aigenweb.langgraph4j.model.enums.ImageCategoryEnum;
import com.leo.aigenweb.langgraph4j.state.WorkflowContext;
import com.leo.aigenweb.langgraph4j.tools.ImageSearchTool;
import com.leo.aigenweb.langgraph4j.tools.LogoGeneratorTool;
import com.leo.aigenweb.langgraph4j.tools.MermaidDiagramTool;
import com.leo.aigenweb.langgraph4j.tools.UndrawIllustrationTool;
import com.leo.aigenweb.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 工作流整体测试（开启图片收集）：用 mock 替换 AI 服务和外部图片工具，验证图的连线、条件边、质检循环和并发分支
 */
@SpringBootTest(properties = "workflow.image-collection.enabled=true")
class CodeGenWorkflowGraphTest {

    @MockitoBean
    private AiCodeGeneratorFacade facade;
    @MockitoBean
    private ImageCollectionPlanService planService;
    @MockitoBean
    private AiCodeGenTypeRoutingServiceFactory routingServiceFactory;
    private final AiCodeGenTypeRoutingService routingService = mock(AiCodeGenTypeRoutingService.class);
    @MockitoBean
    private CodeQualityCheckService qualityService;
    @MockitoBean
    private VueProjectBuilder vueProjectBuilder;
    @MockitoBean
    private ImageSearchTool imageSearchTool;
    @MockitoBean
    private UndrawIllustrationTool undrawTool;
    @MockitoBean
    private MermaidDiagramTool mermaidTool;
    @MockitoBean
    private LogoGeneratorTool logoTool;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    private final List<File> createdDirs = new CopyOnWriteArrayList<>();

    private static ImageResource image(ImageCategoryEnum category, String url) {
        return ImageResource.builder().category(category).description(category.getText()).url(url).build();
    }

    @BeforeEach
    void stubCommon() {
        when(routingServiceFactory.createAiCodeGenTypeRoutingService()).thenReturn(routingService);
        ImageCollectionPlan plan = new ImageCollectionPlan();
        plan.setContentImageTasks(List.of(new ImageCollectionPlan.ImageSearchTask("cat")));
        plan.setIllustrationTasks(List.of(new ImageCollectionPlan.IllustrationTask("happy")));
        plan.setDiagramTasks(List.of(new ImageCollectionPlan.DiagramTask("flowchart LR\nA-->B", "架构")));
        plan.setLogoTasks(List.of(new ImageCollectionPlan.LogoTask("咖啡店")));
        when(planService.planImageCollection(anyString())).thenReturn(plan);
        when(imageSearchTool.searchContentImages(anyString())).thenReturn(List.of(image(ImageCategoryEnum.CONTENT, "https://i/c.jpg")));
        when(undrawTool.searchIllustrations(anyString())).thenReturn(List.of(image(ImageCategoryEnum.ILLUSTRATION, "https://i/i.svg")));
        when(mermaidTool.generateMermaidDiagram(anyString(), anyString())).thenReturn(List.of(image(ImageCategoryEnum.ARCHITECTURE, "https://i/a.png")));
        when(logoTool.generateLogos(anyString())).thenReturn(List.of(image(ImageCategoryEnum.LOGO, "https://i/l.png")));
        when(routingService.routeCodeGenType(anyString())).thenReturn(CodeGenTypeEnum.HTML);
        when(qualityService.checkCodeQuality(anyString())).thenReturn(QualityResult.builder().isValid(true).build());
        when(vueProjectBuilder.buildProject(anyString())).thenReturn(true);
        // 模拟代码生成：在生成目录里写一个文件，供质检读取
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
    void serialWorkflowHtmlPassesQualityAndSkipsBuild() {
        WorkflowContext result = new CodeGenWorkflow().executeWorkflow("做一个咖啡店网站");

        assertEquals(CodeGenTypeEnum.HTML, result.getGenerationType());
        assertEquals(4, result.getImageList().size());
        assertNotNull(result.getAppId());
        // 增强提示词里带上了图片素材
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(facade, times(1)).generateAndSaveCodeStream(prompt.capture(), eq(CodeGenTypeEnum.HTML), eq(result.getAppId()));
        assertTrue(prompt.getValue().contains("可用素材资源") && prompt.getValue().contains("https://i/l.png"), prompt.getValue());
        // HTML 不构建
        verify(vueProjectBuilder, never()).buildProject(anyString());
        assertNull(result.getBuildResultDir());
    }

    @Test
    void serialWorkflowVueProjectIsBuilt() {
        when(routingService.routeCodeGenType(anyString())).thenReturn(CodeGenTypeEnum.VUE_PROJECT);

        WorkflowContext result = new CodeGenWorkflow().executeWorkflow("做一个管理系统");

        verify(vueProjectBuilder, times(1)).buildProject(contains("vue_project_" + result.getAppId()));
        assertNotNull(result.getBuildResultDir());
        assertTrue(result.getBuildResultDir().endsWith("dist"), result.getBuildResultDir());
    }

    @Test
    void qualityFailureRegeneratesWithFixPromptThenPasses() {
        when(qualityService.checkCodeQuality(anyString()))
                .thenReturn(QualityResult.builder().isValid(false).errors(List.of("标签未闭合")).suggestions(List.of("补上 div")).build())
                .thenReturn(QualityResult.builder().isValid(true).build());

        WorkflowContext result = new CodeGenWorkflow().executeWorkflow("做一个网站");

        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(facade, times(2)).generateAndSaveCodeStream(prompts.capture(), any(), eq(result.getAppId()));
        assertFalse(prompts.getAllValues().get(0).contains("标签未闭合"));
        assertTrue(prompts.getAllValues().get(1).contains("标签未闭合") && prompts.getAllValues().get(1).contains("补上 div"));
        // 两次生成使用同一个 appId，才能靠对话记忆在已有代码上修复
        assertEquals(1, result.getRegenerateCount());
    }

    @Test
    void qualityAlwaysFailingStopsAfterRetryLimit() {
        when(qualityService.checkCodeQuality(anyString()))
                .thenReturn(QualityResult.builder().isValid(false).errors(List.of("永远有错")).build());

        WorkflowContext result = new CodeGenWorkflow().executeWorkflow("做一个网站");

        // 首次生成 + 最多 MAX_REGENERATE_COUNT 次重新生成，之后不再循环
        verify(facade, times(1 + WorkflowRouting.MAX_REGENERATE_COUNT)).generateAndSaveCodeStream(anyString(), any(), anyLong());
        assertEquals(WorkflowRouting.MAX_REGENERATE_COUNT + 1, result.getRegenerateCount());
    }

    @Test
    void imageToolFailureDoesNotBreakWorkflow() {
        when(imageSearchTool.searchContentImages(anyString())).thenThrow(new RuntimeException("pexels down"));
        when(planService.planImageCollection(anyString())).thenThrow(new RuntimeException("llm down"));

        WorkflowContext result = new CodeGenWorkflow().executeWorkflow("做一个网站");

        assertTrue(result.getImageList().isEmpty());
        verify(facade, times(1)).generateAndSaveCodeStream(anyString(), any(), anyLong());
    }

    @Test
    void concurrentWorkflowRunsFourCollectorsInParallel() {
        // 四个收集工具都要等到其他工具也开始执行才会返回：只有真正并发才不会超时
        CountDownLatch allStarted = new CountDownLatch(4);
        List<Boolean> parallel = new CopyOnWriteArrayList<>();
        org.mockito.stubbing.Answer<Object> rendezvous = inv -> {
            allStarted.countDown();
            parallel.add(allStarted.await(5, TimeUnit.SECONDS));
            return List.of(image(ImageCategoryEnum.CONTENT, "https://i/x.jpg"));
        };
        when(imageSearchTool.searchContentImages(anyString())).thenAnswer(rendezvous);
        when(undrawTool.searchIllustrations(anyString())).thenAnswer(rendezvous);
        when(mermaidTool.generateMermaidDiagram(anyString(), anyString())).thenAnswer(rendezvous);
        when(logoTool.generateLogos(anyString())).thenAnswer(rendezvous);

        WorkflowContext result = new CodeGenConcurrentWorkflow().executeWorkflow("做一个咖啡店网站");

        assertEquals(4, parallel.size());
        assertTrue(parallel.stream().allMatch(Boolean::booleanValue), "四个收集节点应当并发执行");
        assertEquals(4, result.getImageList().size(), "聚合节点应当汇总四个分支的结果");
        verify(facade, times(1)).generateAndSaveCodeStream(anyString(), eq(CodeGenTypeEnum.HTML), anyLong());
    }

    @Test
    void fluxEmitsStartStepsAndCompletedEvents() {
        List<ServerSentEvent<String>> events = new ArrayList<>(
                new CodeGenWorkflow().executeWorkflowWithFlux("做一个网站", null)
                        .collectList().block(Duration.ofSeconds(60)));

        assertEquals("workflow_start", events.get(0).event());
        assertEquals("workflow_completed", events.get(events.size() - 1).event());
        long steps = events.stream().filter(e -> "step_completed".equals(e.event())).count();
        assertTrue(steps >= 5, "至少应有图片收集、增强、路由、生成、质检五步，实际 " + steps);
        // 步骤事件携带真实节点名，前端可据此展示进度
        assertTrue(events.stream().anyMatch(e -> "step_completed".equals(e.event()) && e.data().contains("\"node\":\"router\"")),
                "应有 router 节点的步骤事件");
        String completed = events.get(events.size() - 1).data();
        assertTrue(completed.contains("\"codeGenType\":\"html\"") && completed.contains("appId"), completed);
        assertFalse(completed.contains("/tmp/") || completed.contains(AppConstant.CODE_OUTPUT_ROOT_DIR), "不应暴露服务器目录");
    }

    @Test
    void fluxReportsErrorEventInsteadOfHanging() {
        when(facade.generateAndSaveCodeStream(anyString(), any(), anyLong())).thenThrow(new RuntimeException("boom"));

        List<ServerSentEvent<String>> events = new CodeGenWorkflow().executeWorkflowWithFlux("做一个网站", null)
                .collectList().block(Duration.ofSeconds(60));

        assertEquals("workflow_error", events.get(events.size() - 1).event());
    }
}
