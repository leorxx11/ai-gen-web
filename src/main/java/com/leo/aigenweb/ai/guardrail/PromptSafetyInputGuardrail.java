package com.leo.aigenweb.ai.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Prompt 安全审查输入护轨：在用户输入交给大模型之前做基础检查。
 * <p>
 * 只是基础的关键词/正则检测，生产环境可以再接入内容安全服务或专门的审核模型。
 */
public class PromptSafetyInputGuardrail implements InputGuardrail {

    /**
     * 输入长度上限。对话输入框限制 1000 字，这里要给选中元素信息、工作流追加的图片素材和质检意见留足余量，
     * 只拦截明显的超长输入（防止刷 token）。
     */
    static final int MAX_INPUT_LENGTH = 10000;

    /**
     * 中文敏感词（直接子串匹配）
     */
    private static final List<String> SENSITIVE_WORDS = List.of("忽略之前的指令", "破解", "绕过", "越狱");

    /**
     * 英文敏感词按单词边界匹配，避免把 hackathon 之类的正常内容误判
     */
    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(?:hack|bypass|jailbreak)\\b")
    );

    /**
     * 注入攻击模式
     */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(?:previous|above|all)\\s+(?:instructions?|commands?|prompts?)"),
            Pattern.compile("(?i)(?:forget|disregard)\\s+(?:everything|all)\\s+(?:above|before)"),
            Pattern.compile("(?i)(?:pretend|act|behave)\\s+(?:as|like)\\s+(?:if|you\\s+are)"),
            Pattern.compile("(?i)system\\s*:\\s*you\\s+are"),
            Pattern.compile("(?i)new\\s+(?:instructions?|commands?|prompts?)\\s*:")
    );

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String input = userMessage.singleText();
        if (input == null || input.isBlank()) {
            return fatal("输入内容不能为空");
        }
        if (input.length() > MAX_INPUT_LENGTH) {
            return fatal("输入内容过长，请精简后重试");
        }
        for (String word : SENSITIVE_WORDS) {
            if (input.contains(word)) {
                return fatal("输入包含不当内容，请修改后重试");
            }
        }
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return fatal("输入包含不当内容，请修改后重试");
            }
        }
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return fatal("检测到恶意输入，请求被拒绝");
            }
        }
        return success();
    }
}
