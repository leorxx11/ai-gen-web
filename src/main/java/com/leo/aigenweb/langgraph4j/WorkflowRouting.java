package com.leo.aigenweb.langgraph4j;

import com.leo.aigenweb.langgraph4j.model.QualityResult;
import com.leo.aigenweb.langgraph4j.state.WorkflowContext;
import com.leo.aigenweb.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.prebuilt.MessagesState;

/**
 * 工作流条件边的路由函数：节点专注业务逻辑，边专注流程控制
 */
@Slf4j
public class WorkflowRouting {

    /**
     * 质检失败后最多重新生成的次数，避免 AI 一直生成不出合格代码时无限循环
     */
    public static final int MAX_REGENERATE_COUNT = 2;

    private WorkflowRouting() {
    }

    /**
     * 代码生成后是否需要构建：HTML 和多文件在生成时已保存，直接结束；Vue 工程需要构建
     */
    public static String routeBuildOrSkip(MessagesState<String> state) {
        CodeGenTypeEnum generationType = WorkflowContext.getContext(state).getGenerationType();
        if (generationType == CodeGenTypeEnum.HTML || generationType == CodeGenTypeEnum.MULTI_FILE) {
            return WorkflowNodeNames.ROUTE_SKIP_BUILD;
        }
        return WorkflowNodeNames.ROUTE_BUILD;
    }

    /**
     * 质检后的路由：通过则按构建规则继续；失败则回到代码生成，但最多重试 {@link #MAX_REGENERATE_COUNT} 次
     */
    public static String routeAfterQualityCheck(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        QualityResult qualityResult = context.getQualityResult();
        boolean passed = qualityResult != null && Boolean.TRUE.equals(qualityResult.getIsValid());
        if (passed) {
            log.info("代码质检通过，继续后续流程");
            return routeBuildOrSkip(state);
        }
        if (context.getRegenerateCount() <= MAX_REGENERATE_COUNT) {
            log.warn("代码质检失败，重新生成代码（第 {} 次）", context.getRegenerateCount());
            return WorkflowNodeNames.ROUTE_FAIL;
        }
        log.error("代码质检连续失败 {} 次，不再重试，继续后续流程", context.getRegenerateCount());
        return routeBuildOrSkip(state);
    }
}
