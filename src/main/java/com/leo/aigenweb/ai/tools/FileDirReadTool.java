package com.leo.aigenweb.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * 文件目录读取工具，使用 Hutool 递归获取目录下的所有文件
 */
@Slf4j
@Component
public class FileDirReadTool extends BaseTool {

    /**
     * 需要忽略的文件和目录
     */
    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules", ".git", "dist", "build", ".DS_Store",
            ".env", "target", ".mvn", ".idea", ".vscode", "coverage"
    );

    /**
     * 需要忽略的文件扩展名
     */
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".log", ".tmp", ".cache", ".lock"
    );

    @Tool("读取目录结构，获取指定目录下的所有文件和子目录信息")
    public String readDir(
            @P("目录的相对路径，为空则读取整个项目结构")
            String relativeDirPath,
            @ToolMemoryId Long appId) {
        try {
            Path path = resolveSafePath(appId, relativeDirPath);
            File targetDir = path.toFile();
            if (!targetDir.exists() || !targetDir.isDirectory()) {
                return "错误：目录不存在或不是目录 - " + relativeDirPath;
            }
            // 递归获取所有文件，并忽略依赖、构建产物等目录
            List<File> allFiles = FileUtil.loopFiles(targetDir, file -> !isInIgnoredPath(targetDir, file));
            StringBuilder structure = new StringBuilder("项目目录结构（每行是一个文件的相对路径）:\n");
            allFiles.stream()
                    .map(file -> targetDir.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/'))
                    .sorted()
                    .forEach(relative -> structure.append(relative).append('\n'));
            return structure.toString();
        } catch (Exception e) {
            String errorMessage = "读取目录结构失败: " + relativeDirPath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    /**
     * 判断文件的路径中是否包含需要忽略的目录或文件
     */
    private boolean isInIgnoredPath(File root, File file) {
        for (Path part : root.toPath().relativize(file.toPath())) {
            String name = part.toString();
            if (IGNORED_NAMES.contains(name) || IGNORED_EXTENSIONS.stream().anyMatch(name::endsWith)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getToolName() {
        return "readDir";
    }

    @Override
    public String getDisplayName() {
        return "读取目录";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeDirPath = arguments.getStr("relativeDirPath");
        if (StrUtil.isEmpty(relativeDirPath)) {
            relativeDirPath = "根目录";
        }
        return String.format("[工具调用] %s %s", getDisplayName(), relativeDirPath);
    }
}
