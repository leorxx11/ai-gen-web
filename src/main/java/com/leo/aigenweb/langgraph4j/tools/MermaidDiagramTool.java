package com.leo.aigenweb.langgraph4j.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.leo.aigenweb.langgraph4j.model.ImageResource;
import com.leo.aigenweb.langgraph4j.model.enums.ImageCategoryEnum;
import com.leo.aigenweb.manager.DarkroomManager;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 架构图绘制工具：用 mermaid-cli 把 Mermaid 文本绘图代码渲染成 PNG，再上传到图床。
 * <p>
 * 需要先安装：npm install -g @mermaid-js/mermaid-cli。
 * 图床不接收 SVG，所以输出 PNG。
 */
@Slf4j
@Component
public class MermaidDiagramTool {

    @Resource
    private DarkroomManager darkroomManager;

    /**
     * mermaid-cli 可执行文件，默认从 PATH 查找
     */
    @Value("${mermaid.cli-path:mmdc}")
    private String mermaidCliPath;

    /**
     * 渲染用的 Chrome 路径，不填则使用 puppeteer 自带的
     */
    @Value("${mermaid.chrome-path:${SCREENSHOT_CHROME_BINARY:}}")
    private String chromePath;

    @Tool("将 Mermaid 代码转换为架构图图片，用于展示系统结构和技术关系")
    public List<ImageResource> generateMermaidDiagram(@P("Mermaid 图表代码") String mermaidCode,
                                                      @P("架构图描述") String description) {
        if (StrUtil.isBlank(mermaidCode)) {
            return new ArrayList<>();
        }
        File diagramFile = null;
        try {
            diagramFile = convertMermaidToPng(mermaidCode);
            if (diagramFile == null) {
                return new ArrayList<>();
            }
            String url = darkroomManager.uploadFile(diagramFile);
            if (StrUtil.isNotBlank(url)) {
                return Collections.singletonList(ImageResource.builder()
                        .category(ImageCategoryEnum.ARCHITECTURE)
                        .description(description)
                        .url(url)
                        .build());
            }
        } catch (Exception e) {
            log.error("生成架构图失败: {}", e.getMessage(), e);
        } finally {
            // 清理临时文件（输出文件所在的临时目录）
            if (diagramFile != null) {
                FileUtil.del(diagramFile.getParentFile());
            }
        }
        return new ArrayList<>();
    }

    /**
     * 将 Mermaid 代码转换为 PNG 图片，失败返回 null
     */
    private File convertMermaidToPng(String mermaidCode) throws Exception {
        File workDir = FileUtil.mkdir(FileUtil.getTmpDir() + File.separator + "mermaid_" + System.nanoTime());
        File input = new File(workDir, "diagram.mmd");
        File output = new File(workDir, "diagram.png");
        FileUtil.writeUtf8String(mermaidCode, input);
        List<String> command = new ArrayList<>();
        command.add(isWindows() ? mermaidCliPath + ".cmd" : mermaidCliPath);
        command.addAll(List.of("-i", input.getAbsolutePath(), "-o", output.getAbsolutePath(),
                "-b", "white", "-s", "2"));
        // root 或容器环境下 Chrome 需要 --no-sandbox，指定 Chrome 路径时一并写入 puppeteer 配置
        File puppeteerConfig = new File(workDir, "puppeteer.json");
        String executable = StrUtil.isBlank(chromePath) ? "" : ",\"executablePath\":\"" + chromePath.replace("\\", "\\\\") + "\"";
        FileUtil.writeString("{\"args\":[\"--no-sandbox\"]" + executable + "}", puppeteerConfig, StandardCharsets.UTF_8);
        command.addAll(List.of("-p", puppeteerConfig.getAbsolutePath()));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        // 必须读取输出，否则缓冲区写满会让子进程阻塞
        Thread drain = Thread.startVirtualThread(() -> {
            try {
                process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            } catch (Exception ignored) {
                // 忽略读取异常
            }
        });
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.error("Mermaid CLI 执行超时");
            FileUtil.del(workDir);
            return null;
        }
        drain.join(1000);
        if (!output.exists() || output.length() == 0) {
            log.error("Mermaid CLI 执行失败，退出码: {}", process.exitValue());
            FileUtil.del(workDir);
            return null;
        }
        return output;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
