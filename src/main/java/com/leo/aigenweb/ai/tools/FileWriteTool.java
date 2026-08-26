package com.leo.aigenweb.ai.tools;

import com.leo.aigenweb.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Slf4j
public class FileWriteTool {

    @Tool("写入文件到指定路径")
    public String writeFile(
            @P("文件相对路径")
            String relativeFilePath,
            @P("要写入文件的内容")
            String content,
            @ToolMemoryId Long appid){
        try {
            Path path = Paths.get(relativeFilePath);
            if(!path.isAbsolute()){
                String projectDirName = "vue_project_" + appid;
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                // 拼接
                path = projectRoot.resolve(relativeFilePath);
            }
            Path parentDir = path.getParent();
            if(parentDir != null){
                Files.createDirectories(parentDir);
            }
            Files.write(path, content.getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功写入文件：{}",path.toAbsolutePath());
            return "文件写入成功：" + relativeFilePath;

        }catch (Exception e){
            String errorMessage = "文件写入失败：" + relativeFilePath + ", 错误：" + e.getMessage();
            log.error(errorMessage,e);
            return errorMessage;
        }
    }
}
