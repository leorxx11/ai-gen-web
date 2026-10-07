package com.leo.aigenweb.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.leo.aigenweb.constant.AppConstant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class FileToolsTest {

    /**
     * 使用一个不会和真实应用冲突的 appId
     */
    private static final Long APP_ID = 987654321L;

    private final FileWriteTool write = new FileWriteTool();
    private final FileReadTool read = new FileReadTool();
    private final FileModifyTool modify = new FileModifyTool();
    private final FileDirReadTool dirRead = new FileDirReadTool();
    private final FileDeleteTool delete = new FileDeleteTool();

    private File root() {
        return new File(AppConstant.CODE_OUTPUT_ROOT_DIR, "vue_project_" + APP_ID);
    }

    @BeforeEach
    @AfterEach
    void cleanup() {
        FileUtil.del(root());
    }

    @Test
    void writeReadModifyDeleteRoundTrip() {
        assertTrue(write.writeFile("src/pages/A.vue", "<p>hello</p>", APP_ID).contains("成功"));
        assertEquals("<p>hello</p>", read.readFile("src/pages/A.vue", APP_ID));

        assertTrue(modify.modifyFile("src/pages/A.vue", "hello", "world", APP_ID).contains("成功"));
        assertEquals("<p>world</p>", read.readFile("src/pages/A.vue", APP_ID));
        assertTrue(modify.modifyFile("src/pages/A.vue", "not-found", "x", APP_ID).contains("未找到"));

        assertTrue(delete.deleteFile("src/pages/A.vue", APP_ID).contains("成功"));
        assertTrue(read.readFile("src/pages/A.vue", APP_ID).contains("不存在"));
    }

    @Test
    void dirReadListsRelativePathsAndSkipsIgnored() {
        write.writeFile("src/main.js", "1", APP_ID);
        write.writeFile("src/pages/Home.vue", "2", APP_ID);
        write.writeFile("node_modules/x/index.js", "3", APP_ID);
        String out = dirRead.readDir("", APP_ID);
        assertTrue(out.contains("src/main.js"), out);
        assertTrue(out.contains("src/pages/Home.vue"), out);
        assertFalse(out.contains("node_modules"), out);
    }

    @Test
    void importantFilesCannotBeDeleted() {
        write.writeFile("package.json", "{}", APP_ID);
        assertTrue(delete.deleteFile("package.json", APP_ID).contains("不允许删除"));
        assertTrue(new File(root(), "package.json").exists());
    }

    @Test
    void pathsOutsideProjectAreRejected() throws Exception {
        File outside = new File(AppConstant.CODE_OUTPUT_ROOT_DIR, "outside_" + APP_ID + ".txt");
        FileUtil.writeUtf8String("secret", outside);
        try {
            // ../ 越界读取、写入、删除
            assertTrue(read.readFile("../outside_" + APP_ID + ".txt", APP_ID).contains("超出项目目录"));
            assertTrue(write.writeFile("../outside_" + APP_ID + ".txt", "pwned", APP_ID).contains("超出项目目录"));
            assertTrue(delete.deleteFile("../outside_" + APP_ID + ".txt", APP_ID).contains("超出项目目录"));
            // 越界的绝对路径
            assertTrue(read.readFile(outside.getAbsolutePath(), APP_ID).contains("超出项目目录"));
            assertTrue(dirRead.readDir("..", APP_ID).contains("超出项目目录"));
            assertEquals("secret", FileUtil.readUtf8String(outside));
        } finally {
            FileUtil.del(outside);
        }
    }

    @Test
    void toolMetadataIsConsistent() {
        for (BaseTool tool : new BaseTool[]{write, read, modify, dirRead, delete}) {
            assertNotNull(tool.getToolName());
            assertTrue(tool.generateToolRequestResponse().contains(tool.getDisplayName()));
        }
        String modifyText = modify.generateToolExecutedResult(JSONUtil.parseObj(
                "{\"relativeFilePath\":\"src/a.vue\",\"oldContent\":\"A\",\"newContent\":\"B\"}"));
        assertTrue(modifyText.contains("替换前") && modifyText.contains("替换后"), modifyText);
    }
}
