package com.leo.aigenweb.langgraph4j.node;

import cn.hutool.core.util.IdUtil;
import com.leo.aigenweb.constant.AppConstant;
import com.leo.aigenweb.core.AiCodeGeneratorFacade;
import com.leo.aigenweb.langgraph4j.model.QualityResult;
import com.leo.aigenweb.langgraph4j.state.WorkflowContext;
import com.leo.aigenweb.model.enums.CodeGenTypeEnum;
import com.leo.aigenweb.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 代码生成节点：调用 AI 代码生成门面生成网站，并同步等待流式输出完成
 */
@Slf4j
public class CodeGeneratorNode {

    private CodeGeneratorNode() {
    }

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 代码生成");
            // 构造用户消息（包含增强提示词，质检失败时改为错误修复信息）
            String userMessage = buildUserMessage(context);
            CodeGenTypeEnum generationType = context.getGenerationType();
            // appId 决定保存目录和对话记忆，必须在整个工作流内保持不变（质检失败重新生成时靠它延续上下文）
            if (context.getAppId() == null) {
                context.setAppId(IdUtil.getSnowflakeNextId());
            }
            Long appId = context.getAppId();
            AiCodeGeneratorFacade codeGeneratorFacade = SpringContextUtil.getBean(AiCodeGeneratorFacade.class);
            log.info("开始生成代码，类型: {} ({})，appId: {}", generationType.getValue(), generationType.getText(), appId);
            Flux<String> codeStream = codeGeneratorFacade.generateAndSaveCodeStream(userMessage, generationType, appId);
            // 同步等待流式输出完成，最多 10 分钟
            codeStream.blockLast(Duration.ofMinutes(10));
            String generatedCodeDir = String.format("%s/%s_%s", AppConstant.CODE_OUTPUT_ROOT_DIR, generationType.getValue(), appId);
            log.info("AI 代码生成完成，生成目录: {}", generatedCodeDir);
            context.setCurrentStep("代码生成");
            context.setGeneratedCodeDir(generatedCodeDir);
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 构造用户消息，如果存在质检失败结果则改为错误修复信息
     */
    static String buildUserMessage(WorkflowContext context) {
        String userMessage = context.getEnhancedPrompt();
        QualityResult qualityResult = context.getQualityResult();
        if (isQualityCheckFailed(qualityResult)) {
            // 错误修复信息作为新的提示词，借助对话记忆起到“在已有代码上修改”的作用
            userMessage = buildErrorFixPrompt(qualityResult);
        }
        return userMessage;
    }

    /**
     * 判断质检是否失败
     */
    static boolean isQualityCheckFailed(QualityResult qualityResult) {
        return qualityResult != null
                && Boolean.FALSE.equals(qualityResult.getIsValid())
                && qualityResult.getErrors() != null
                && !qualityResult.getErrors().isEmpty();
    }

    /**
     * 构造错误修复提示词
     */
    static String buildErrorFixPrompt(QualityResult qualityResult) {
        StringBuilder errorInfo = new StringBuilder();
        errorInfo.append("\n\n## 上次生成的代码存在以下问题，请修复：\n");
        qualityResult.getErrors().forEach(error -> errorInfo.append("- ").append(error).append("\n"));
        if (qualityResult.getSuggestions() != null && !qualityResult.getSuggestions().isEmpty()) {
            errorInfo.append("\n## 修复建议：\n");
            qualityResult.getSuggestions().forEach(suggestion -> errorInfo.append("- ").append(suggestion).append("\n"));
        }
        errorInfo.append("\n请根据上述问题和建议重新生成代码，确保修复所有提到的问题。");
        return errorInfo.toString();
    }
}
