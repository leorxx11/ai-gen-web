package com.leo.aigenweb.langgraph4j;

/**
 * 工作流节点名称常量：节点名在加节点、加边、路由函数中多处使用，统一维护避免写错
 */
public interface WorkflowNodeNames {

    String IMAGE_COLLECTOR = "image_collector";
    String IMAGE_PLAN = "image_plan";
    String CONTENT_IMAGE_COLLECTOR = "content_image_collector";
    String ILLUSTRATION_COLLECTOR = "illustration_collector";
    String DIAGRAM_COLLECTOR = "diagram_collector";
    String LOGO_COLLECTOR = "logo_collector";
    String IMAGE_AGGREGATOR = "image_aggregator";
    String PROMPT_ENHANCER = "prompt_enhancer";
    String ROUTER = "router";
    String CODE_GENERATOR = "code_generator";
    String CODE_QUALITY_CHECK = "code_quality_check";
    String PROJECT_BUILDER = "project_builder";

    /**
     * 质检后路由的目标
     */
    String ROUTE_BUILD = "build";
    String ROUTE_SKIP_BUILD = "skip_build";
    String ROUTE_FAIL = "fail";
}
