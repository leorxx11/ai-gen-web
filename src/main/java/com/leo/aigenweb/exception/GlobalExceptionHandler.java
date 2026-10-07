package com.leo.aigenweb.exception;

import cn.hutool.json.JSONUtil;
import com.leo.aigenweb.common.BaseResponse;
import com.leo.aigenweb.common.ResultUtils;
import dev.langchain4j.guardrail.InputGuardrailException;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        // SSE 请求要以事件流的格式返回错误，前端才能显示具体的提示
        if (handleSseError(e.getCode(), e.getMessage())) {
            return null;
        }
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    /**
     * 输入护轨拒绝了用户的 Prompt。异常在调用 AI 服务时同步抛出，这里转成带具体原因的提示。
     */
    @ExceptionHandler(InputGuardrailException.class)
    public BaseResponse<?> inputGuardrailExceptionHandler(InputGuardrailException e) {
        log.warn("输入护轨拦截: {}", e.getMessage());
        String message = extractGuardrailMessage(e.getMessage());
        if (handleSseError(ErrorCode.PARAMS_ERROR.getCode(), message)) {
            return null;
        }
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, message);
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        if (handleSseError(ErrorCode.SYSTEM_ERROR.getCode(), "系统错误")) {
            return null;
        }
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

    /**
     * 处理 SSE 请求的错误响应。限流、参数校验等异常发生在进入流之前，没有机会通过流返回，
     * 这里直接按 SSE 格式写出 business-error 事件（不用标准的 error 事件名，避免与浏览器的连接错误事件混淆）和 done 事件。
     *
     * @return true 表示是 SSE 请求并已处理；false 表示不是 SSE 请求
     */
    private boolean handleSseError(int errorCode, String errorMessage) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return false;
        }
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();
        if (response == null || !isSseRequest(request)) {
            return false;
        }
        try {
            response.setContentType("text/event-stream");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Cache-Control", "no-cache");
            String errorJson = JSONUtil.toJsonStr(Map.of("error", true, "code", errorCode, "message", errorMessage));
            response.getWriter().write("event: business-error\ndata: " + errorJson + "\n\n");
            response.getWriter().write("event: done\ndata: {}\n\n");
            response.getWriter().flush();
        } catch (IOException ioException) {
            log.error("写入 SSE 错误响应失败", ioException);
        }
        // 即使写入失败，这也是 SSE 请求，不应再按普通 JSON 响应
        return true;
    }

    /**
     * 护轨异常消息形如 "The guardrail X failed with this message: 具体原因"，取出面向用户的那部分
     */
    private String extractGuardrailMessage(String exceptionMessage) {
        final String marker = "failed with this message: ";
        if (exceptionMessage != null) {
            int index = exceptionMessage.indexOf(marker);
            if (index >= 0) {
                return exceptionMessage.substring(index + marker.length()).trim();
            }
        }
        return "输入内容未通过安全审查，请修改后重试";
    }

    private boolean isSseRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/event-stream");
    }
}
