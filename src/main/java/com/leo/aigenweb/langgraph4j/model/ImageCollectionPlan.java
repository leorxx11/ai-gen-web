package com.leo.aigenweb.langgraph4j.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 图片收集计划：由 AI 根据网站需求规划要收集哪些图片，再由程序并发执行
 */
@Data
public class ImageCollectionPlan implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 内容图片搜索任务列表
     */
    private List<ImageSearchTask> contentImageTasks;

    /**
     * 插画图片搜索任务列表
     */
    private List<IllustrationTask> illustrationTasks;

    /**
     * 架构图生成任务列表
     */
    private List<DiagramTask> diagramTasks;

    /**
     * Logo 生成任务列表
     */
    private List<LogoTask> logoTasks;

    /**
     * 内容图片搜索任务，对应 ImageSearchTool.searchContentImages(String query)
     */
    public record ImageSearchTask(String query) implements Serializable {
    }

    /**
     * 插画图片搜索任务，对应 UndrawIllustrationTool.searchIllustrations(String query)
     */
    public record IllustrationTask(String query) implements Serializable {
    }

    /**
     * 架构图生成任务，对应 MermaidDiagramTool.generateMermaidDiagram(String mermaidCode, String description)
     */
    public record DiagramTask(String mermaidCode, String description) implements Serializable {
    }

    /**
     * Logo 生成任务，对应 LogoGeneratorTool.generateLogos(String description)
     */
    public record LogoTask(String description) implements Serializable {
    }
}
