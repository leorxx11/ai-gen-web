package com.leo.aigenweb.controller;

import cn.hutool.core.util.StrUtil;
import com.leo.aigenweb.common.BaseResponse;
import com.leo.aigenweb.common.ResultUtils;
import com.leo.aigenweb.exception.ErrorCode;
import com.leo.aigenweb.exception.ThrowUtils;
import com.leo.aigenweb.langgraph4j.AbstractCodeGenWorkflow;
import com.leo.aigenweb.langgraph4j.CodeGenConcurrentWorkflow;
import com.leo.aigenweb.langgraph4j.CodeGenWorkflow;
import com.leo.aigenweb.langgraph4j.state.WorkflowContext;
import com.leo.aigenweb.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

/**
 * 工作流控制器：演示 LangGraph4j 代码生成工作流的同步执行和 SSE 流式执行。
 * 工作流会消耗 LLM 额度，因此必须登录后才能调用。
 */
@Slf4j
@RestController
@RequestMapping("/workflow")
public class WorkflowSseController {

    /**
     * 提示词长度上限，与对话输入框保持一致
     */
    private static final int MAX_PROMPT_LENGTH = 1000;

    @Resource
    private UserService userService;

    /**
     * 同步执行工作流，执行完成后返回摘要信息（不返回服务器目录等内部信息）
     */
    @PostMapping("/execute")
    public BaseResponse<Map<String, Object>> executeWorkflow(@RequestParam String prompt,
                                                             @RequestParam(defaultValue = "true") boolean concurrent,
                                                             HttpServletRequest request) {
        checkAccess(prompt, request);
        log.info("收到同步工作流执行请求: {}", prompt);
        WorkflowContext context = chooseWorkflow(concurrent).executeWorkflow(prompt);
        Map<String, Object> summary = new HashMap<>();
        summary.put("appId", String.valueOf(context.getAppId()));
        summary.put("codeGenType", context.getGenerationType() == null ? null : context.getGenerationType().getValue());
        summary.put("imageCount", context.getImageList() == null ? 0 : context.getImageList().size());
        summary.put("qualityPassed", context.getQualityResult() != null && Boolean.TRUE.equals(context.getQualityResult().getIsValid()));
        return ResultUtils.success(summary);
    }

    /**
     * 流式执行工作流：每完成一个步骤推送一个 SSE 事件
     * （workflow_start / step_completed / workflow_completed / workflow_error）
     */
    @GetMapping(value = "/execute-flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> executeWorkflowWithFlux(@RequestParam String prompt,
                                                                 @RequestParam(defaultValue = "true") boolean concurrent,
                                                                 HttpServletRequest request) {
        checkAccess(prompt, request);
        log.info("收到 Flux 工作流执行请求: {}", prompt);
        return chooseWorkflow(concurrent).executeWorkflowWithFlux(prompt, null);
    }

    private void checkAccess(String prompt, HttpServletRequest request) {
        ThrowUtils.throwIf(StrUtil.isBlank(prompt), ErrorCode.PARAMS_ERROR, "提示词不能为空");
        ThrowUtils.throwIf(prompt.length() > MAX_PROMPT_LENGTH, ErrorCode.PARAMS_ERROR, "提示词过长");
        // 未登录会在这里抛出异常
        userService.getLoginUser(request);
    }

    private AbstractCodeGenWorkflow chooseWorkflow(boolean concurrent) {
        return concurrent ? new CodeGenConcurrentWorkflow() : new CodeGenWorkflow();
    }
}
