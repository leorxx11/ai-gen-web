package com.leo.aigenweb.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.leo.aigenweb.model.entity.App;
import com.leo.aigenweb.service.AppService;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用真实数据库和 Redis 验证清理器的装配与真实查询：有应用记录（含逻辑删除）的目录保留，没有的删除
 */
@SpringBootTest
class OrphanCodeCleanerIntegrationTest {

    @Resource
    private OrphanCodeCleaner cleaner;

    @Resource
    private AppService appService;

    @Test
    void keepsDirOfRealAndSoftDeletedAppsAndRemovesOrphanWithRealDatabase(@TempDir File root) {
        ReflectionTestUtils.setField(cleaner, "rootDir", root.getAbsolutePath());
        App real = newApp("清理测试-正常");
        App softDeleted = newApp("清理测试-已删除");
        try {
            // 应用的“删除”是逻辑删除，行还在，只是 isDelete=1
            assertTrue(appService.removeById(softDeleted.getId()));
            assertNull(appService.getById(softDeleted.getId()), "默认查询应当查不到逻辑删除的应用");

            File realDir = oldDir(root, "html_" + real.getId());
            File softDeletedDir = oldDir(root, "html_" + softDeleted.getId());
            File orphanDir = oldDir(root, "html_" + IdUtil.getSnowflakeNextId());

            int deleted = cleaner.cleanup(Duration.ofHours(24));

            assertEquals(1, deleted);
            assertTrue(realDir.exists(), "有应用记录的目录不能被清理");
            assertTrue(softDeletedDir.exists(), "逻辑删除的应用仍可能被恢复，目录不能被清理");
            assertFalse(orphanDir.exists(), "没有应用记录的旧目录应当被清理");
        } finally {
            // 物理删除测试数据，避免在数据库里留下垃圾行
            LogicDeleteManager.execWithoutLogicDelete(() -> {
                appService.removeById(real.getId());
                appService.removeById(softDeleted.getId());
            });
        }
    }

    private App newApp(String name) {
        App app = new App();
        app.setAppName(name);
        app.setInitPrompt("clean");
        app.setCodeGenType("html");
        app.setUserId(0L);
        assertTrue(appService.save(app));
        return app;
    }

    private File oldDir(File root, String name) {
        File dir = new File(root, name);
        FileUtil.writeUtf8String("x", new File(dir, "index.html"));
        assertTrue(dir.setLastModified(System.currentTimeMillis() - Duration.ofHours(48).toMillis()));
        return dir;
    }
}
