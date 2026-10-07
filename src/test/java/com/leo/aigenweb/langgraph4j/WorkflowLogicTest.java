package com.leo.aigenweb.langgraph4j;

import cn.hutool.core.io.FileUtil;
import com.leo.aigenweb.langgraph4j.model.ImageResource;
import com.leo.aigenweb.langgraph4j.model.QualityResult;
import com.leo.aigenweb.langgraph4j.model.enums.ImageCategoryEnum;
import com.leo.aigenweb.langgraph4j.state.WorkflowContext;
import com.leo.aigenweb.model.enums.CodeGenTypeEnum;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工作流中不依赖 Spring 和 AI 的纯逻辑：路由函数、提示词拼接、修复提示词、代码文件读取
 */
class WorkflowLogicTest {

    private MessagesState<String> stateOf(WorkflowContext context) {
        return new MessagesState<>(Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, context));
    }

    private WorkflowContext context(CodeGenTypeEnum type, Boolean valid, int regenerateCount) {
        return WorkflowContext.builder()
                .generationType(type)
                .regenerateCount(regenerateCount)
                .qualityResult(valid == null ? null : QualityResult.builder().isValid(valid).build())
                .build();
    }

    @Test
    void routeBuildOrSkipByGenerationType() {
        assertEquals("skip_build", WorkflowRouting.routeBuildOrSkip(stateOf(context(CodeGenTypeEnum.HTML, true, 0))));
        assertEquals("skip_build", WorkflowRouting.routeBuildOrSkip(stateOf(context(CodeGenTypeEnum.MULTI_FILE, true, 0))));
        assertEquals("build", WorkflowRouting.routeBuildOrSkip(stateOf(context(CodeGenTypeEnum.VUE_PROJECT, true, 0))));
    }

    @Test
    void routeAfterQualityCheckPassedFollowsBuildRule() {
        assertEquals("skip_build", WorkflowRouting.routeAfterQualityCheck(stateOf(context(CodeGenTypeEnum.HTML, true, 0))));
        assertEquals("build", WorkflowRouting.routeAfterQualityCheck(stateOf(context(CodeGenTypeEnum.VUE_PROJECT, true, 0))));
    }

    @Test
    void routeAfterQualityCheckFailureRetriesUpToLimitThenContinues() {
        // 未超过上限：回到代码生成
        assertEquals("fail", WorkflowRouting.routeAfterQualityCheck(stateOf(context(CodeGenTypeEnum.HTML, false, 1))));
        assertEquals("fail", WorkflowRouting.routeAfterQualityCheck(
                stateOf(context(CodeGenTypeEnum.HTML, false, WorkflowRouting.MAX_REGENERATE_COUNT))));
        // 超过上限：不再无限循环，按构建规则继续
        int over = WorkflowRouting.MAX_REGENERATE_COUNT + 1;
        assertEquals("skip_build", WorkflowRouting.routeAfterQualityCheck(stateOf(context(CodeGenTypeEnum.HTML, false, over))));
        assertEquals("build", WorkflowRouting.routeAfterQualityCheck(stateOf(context(CodeGenTypeEnum.VUE_PROJECT, false, over))));
        // 没有质检结果视为失败
        assertEquals("fail", WorkflowRouting.routeAfterQualityCheck(stateOf(context(CodeGenTypeEnum.HTML, null, 1))));
    }

    @Test
    void enhancedPromptAppendsImagesOrFallsBackToString() throws Exception {
        Method build = Class.forName("com.leo.aigenweb.langgraph4j.node.PromptEnhancerNode")
                .getDeclaredMethod("buildEnhancedPrompt", String.class, List.class, String.class);
        build.setAccessible(true);
        // 没有图片：原样返回
        assertEquals("做个网站", build.invoke(null, "做个网站", null, ""));
        // 有图片对象
        String withImages = (String) build.invoke(null, "做个网站", List.of(
                ImageResource.builder().category(ImageCategoryEnum.LOGO).description("品牌 Logo").url("https://i/logo.png").build()), null);
        assertTrue(withImages.startsWith("做个网站"));
        assertTrue(withImages.contains("## 可用素材资源") && withImages.contains("LOGO图片：品牌 Logo（https://i/logo.png）"), withImages);
        // 只有字符串
        String withStr = (String) build.invoke(null, "做个网站", List.of(), "- 图A：https://i/a.png");
        assertTrue(withStr.contains("- 图A：https://i/a.png"));
    }

    @Test
    void errorFixPromptListsErrorsAndSuggestions() throws Exception {
        Class<?> node = Class.forName("com.leo.aigenweb.langgraph4j.node.CodeGeneratorNode");
        Method failed = node.getDeclaredMethod("isQualityCheckFailed", QualityResult.class);
        Method fix = node.getDeclaredMethod("buildErrorFixPrompt", QualityResult.class);
        failed.setAccessible(true);
        fix.setAccessible(true);
        QualityResult bad = QualityResult.builder().isValid(false)
                .errors(List.of("标签未闭合")).suggestions(List.of("补上 </div>")).build();
        assertTrue((Boolean) failed.invoke(null, bad));
        assertFalse((Boolean) failed.invoke(null, QualityResult.builder().isValid(true).build()));
        assertFalse((Boolean) failed.invoke(null, new Object[]{null}));
        // isValid=false 但没有具体错误：不触发修复
        assertFalse((Boolean) failed.invoke(null, QualityResult.builder().isValid(false).errors(List.of()).build()));
        String prompt = (String) fix.invoke(null, bad);
        assertTrue(prompt.contains("标签未闭合") && prompt.contains("补上 </div>") && prompt.contains("请根据上述问题"), prompt);
    }

    @Test
    void readCodeFilesSkipsDependenciesAndNonCode(@TempDir File dir) throws Exception {
        FileUtil.writeUtf8String("<h1>hi</h1>", new File(dir, "index.html"));
        FileUtil.writeUtf8String("export {}", new File(dir, "src/main.js"));
        FileUtil.writeUtf8String("x", new File(dir, "node_modules/a/index.js"));
        FileUtil.writeUtf8String("x", new File(dir, "dist/out.js"));
        FileUtil.writeUtf8String("png", new File(dir, "logo.png"));
        FileUtil.writeUtf8String("secret", new File(dir, ".env.js"));
        Method read = Class.forName("com.leo.aigenweb.langgraph4j.node.CodeQualityCheckNode")
                .getDeclaredMethod("readAndConcatenateCodeFiles", String.class);
        read.setAccessible(true);
        String content = (String) read.invoke(null, dir.getAbsolutePath());
        assertTrue(content.contains("index.html") && content.contains("<h1>hi</h1>") && content.contains("src/main.js".replace('/', File.separatorChar)), content);
        assertFalse(content.contains("node_modules") || content.contains("dist") || content.contains("logo.png") || content.contains("secret"), content);
        assertEquals("", read.invoke(null, "/no/such/dir"));
        assertEquals("", read.invoke(null, new Object[]{null}));
    }
}
