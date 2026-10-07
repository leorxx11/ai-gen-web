package com.leo.aigenweb.langgraph4j.node;

import com.leo.aigenweb.ai.AiCodeGenTypeRoutingService;
import com.leo.aigenweb.ai.AiCodeGenTypeRoutingServiceFactory;
import com.leo.aigenweb.langgraph4j.state.WorkflowContext;
import com.leo.aigenweb.model.enums.CodeGenTypeEnum;
import com.leo.aigenweb.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 智能路由节点：根据用户原始提示词选择网站生成方式，复用项目已有的 AiCodeGenTypeRoutingService
 */
@Slf4j
public class RouterNode {

    private RouterNode() {
    }

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 智能路由");
            CodeGenTypeEnum generationType;
            try {
                // 每次新建路由服务（内部使用多例模型），并发执行的工作流之间不会互相阻塞
                AiCodeGenTypeRoutingServiceFactory routingServiceFactory = SpringContextUtil.getBean(AiCodeGenTypeRoutingServiceFactory.class);
                AiCodeGenTypeRoutingService routingService = routingServiceFactory.createAiCodeGenTypeRoutingService();
                generationType = routingService.routeCodeGenType(context.getOriginalPrompt());
                log.info("AI智能路由完成，选择类型: {} ({})", generationType.getValue(), generationType.getText());
            } catch (Exception e) {
                log.error("AI智能路由失败，使用默认HTML类型: {}", e.getMessage());
                generationType = CodeGenTypeEnum.HTML;
            }
            context.setCurrentStep("智能路由");
            context.setGenerationType(generationType);
            return WorkflowContext.saveContext(context);
        });
    }
}
