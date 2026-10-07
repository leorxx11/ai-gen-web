package com.leo.aigenweb.langgraph4j.node;

import com.leo.aigenweb.core.builder.VueProjectBuilder;
import com.leo.aigenweb.langgraph4j.state.WorkflowContext;
import com.leo.aigenweb.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 项目构建节点：只有 Vue 工程需要构建，HTML / 多文件在生成时已保存（是否进入本节点由条件边决定）
 */
@Slf4j
public class ProjectBuilderNode {

    private ProjectBuilderNode() {
    }

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 项目构建");
            String generatedCodeDir = context.getGeneratedCodeDir();
            String buildResultDir;
            try {
                VueProjectBuilder vueBuilder = SpringContextUtil.getBean(VueProjectBuilder.class);
                // 执行 Vue 项目构建（npm install + npm run build）
                if (vueBuilder.buildProject(generatedCodeDir)) {
                    buildResultDir = generatedCodeDir + File.separator + "dist";
                    log.info("Vue 项目构建成功，dist 目录: {}", buildResultDir);
                } else {
                    log.error("Vue 项目构建失败: {}", generatedCodeDir);
                    buildResultDir = generatedCodeDir;
                }
            } catch (Exception e) {
                log.error("Vue 项目构建异常: {}", e.getMessage(), e);
                // 异常时返回原路径
                buildResultDir = generatedCodeDir;
            }
            context.setCurrentStep("项目构建");
            context.setBuildResultDir(buildResultDir);
            log.info("项目构建节点完成，最终目录: {}", buildResultDir);
            return WorkflowContext.saveContext(context);
        });
    }
}
