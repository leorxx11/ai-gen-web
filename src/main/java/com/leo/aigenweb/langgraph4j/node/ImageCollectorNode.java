package com.leo.aigenweb.langgraph4j.node;

import com.leo.aigenweb.langgraph4j.ai.ImageCollectionPlanService;
import com.leo.aigenweb.langgraph4j.model.ImageCollectionPlan;
import com.leo.aigenweb.langgraph4j.model.ImageResource;
import com.leo.aigenweb.langgraph4j.state.WorkflowContext;
import com.leo.aigenweb.langgraph4j.tools.ImageSearchTool;
import com.leo.aigenweb.langgraph4j.tools.LogoGeneratorTool;
import com.leo.aigenweb.langgraph4j.tools.MermaidDiagramTool;
import com.leo.aigenweb.langgraph4j.tools.UndrawIllustrationTool;
import com.leo.aigenweb.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 图片收集节点：先让 AI 规划需要的图片，再在节点内部并发调用各图片工具收集
 */
@Slf4j
public class ImageCollectorNode {

    /**
     * 收集图片数量上限，防止增强后的提示词过长
     */
    public static final int MAX_IMAGES = 30;

    private ImageCollectorNode() {
    }

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 图片收集");
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            List<ImageResource> collectedImages = new ArrayList<>();
            try {
                // 第一步：获取图片收集计划
                ImageCollectionPlanService planService = SpringContextUtil.getBean(ImageCollectionPlanService.class);
                ImageCollectionPlan plan = planService.planImageCollection(context.getOriginalPrompt());
                // 第二步：并发执行各种图片收集任务
                collectedImages = collectConcurrently(plan);
                log.info("并发图片收集完成，共收集到 {} 张图片", collectedImages.size());
            } catch (Exception e) {
                // 图片只是增强素材，失败不应该让整个工作流失败
                log.error("图片收集失败: {}", e.getMessage(), e);
            }
            stopWatch.stop();
            log.info("图片收集总耗时: {} ms", stopWatch.getTotalTimeMillis());
            context.setCurrentStep("图片收集");
            context.setImageList(limit(collectedImages));
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 按计划并发调用各图片工具，汇总结果
     */
    static List<ImageResource> collectConcurrently(ImageCollectionPlan plan) {
        List<ImageResource> result = new ArrayList<>();
        if (plan == null) {
            return result;
        }
        List<CompletableFuture<List<ImageResource>>> futures = new ArrayList<>();
        // 图片收集是 IO 密集型任务，使用虚拟线程
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            if (plan.getContentImageTasks() != null) {
                ImageSearchTool tool = SpringContextUtil.getBean(ImageSearchTool.class);
                plan.getContentImageTasks().forEach(task ->
                        futures.add(submit(executor, () -> tool.searchContentImages(task.query()))));
            }
            if (plan.getIllustrationTasks() != null) {
                UndrawIllustrationTool tool = SpringContextUtil.getBean(UndrawIllustrationTool.class);
                plan.getIllustrationTasks().forEach(task ->
                        futures.add(submit(executor, () -> tool.searchIllustrations(task.query()))));
            }
            if (plan.getDiagramTasks() != null) {
                MermaidDiagramTool tool = SpringContextUtil.getBean(MermaidDiagramTool.class);
                plan.getDiagramTasks().forEach(task ->
                        futures.add(submit(executor, () -> tool.generateMermaidDiagram(task.mermaidCode(), task.description()))));
            }
            if (plan.getLogoTasks() != null) {
                LogoGeneratorTool tool = SpringContextUtil.getBean(LogoGeneratorTool.class);
                plan.getLogoTasks().forEach(task ->
                        futures.add(submit(executor, () -> tool.generateLogos(task.description()))));
            }
            // 等待所有任务完成并收集结果
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
        for (CompletableFuture<List<ImageResource>> future : futures) {
            List<ImageResource> images = future.join();
            if (images != null) {
                result.addAll(images);
            }
        }
        return result;
    }

    /**
     * 提交任务；单个任务失败只记录日志，不影响其他任务
     */
    private static CompletableFuture<List<ImageResource>> submit(ExecutorService executor, Supplier<List<ImageResource>> task) {
        return CompletableFuture.supplyAsync(task, executor).exceptionally(e -> {
            log.error("图片收集任务失败: {}", e.getMessage(), e);
            return List.of();
        });
    }

    static List<ImageResource> limit(List<ImageResource> images) {
        return images.size() > MAX_IMAGES ? new ArrayList<>(images.subList(0, MAX_IMAGES)) : images;
    }
}
