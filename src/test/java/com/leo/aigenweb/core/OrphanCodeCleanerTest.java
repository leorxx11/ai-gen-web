package com.leo.aigenweb.core;

import cn.hutool.core.io.FileUtil;
import com.leo.aigenweb.model.entity.App;
import com.leo.aigenweb.service.AppService;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 孤儿代码目录清理器：不启动 Spring，用临时目录和 mock 验证每一条安全约束
 */
class OrphanCodeCleanerTest {

    private static final Duration TTL = Duration.ofHours(24);

    @TempDir
    File root;

    private final AppService appService = mock(AppService.class);
    private final RedisChatMemoryStore memoryStore = mock(RedisChatMemoryStore.class);
    private final OrphanCodeCleaner cleaner = new OrphanCodeCleaner();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cleaner, "rootDir", root.getAbsolutePath());
        ReflectionTestUtils.setField(cleaner, "appService", appService);
        ReflectionTestUtils.setField(cleaner, "redisChatMemoryStore", memoryStore);
    }

    /**
     * 创建一个目录（里面放一个文件），并把修改时间设为 hoursAgo 小时前
     */
    private File dir(String name, long hoursAgo) {
        File dir = new File(root, name);
        FileUtil.writeUtf8String("x", new File(dir, "index.html"));
        assertTrue(dir.setLastModified(System.currentTimeMillis() - Duration.ofHours(hoursAgo).toMillis()));
        return dir;
    }

    @Test
    void deletesOldOrphanDirsAndTheirMemory() {
        File orphan = dir("html_2107820259536330752", 48);
        File orphanVue = dir("vue_project_1", 30);
        when(appService.getById(anyLong())).thenReturn(null);

        int deleted = cleaner.cleanup(TTL);

        assertEquals(2, deleted);
        assertFalse(orphan.exists());
        assertFalse(orphanVue.exists());
        verify(memoryStore).deleteMessages(2107820259536330752L);
        verify(memoryStore).deleteMessages(1L);
    }

    @Test
    void keepsDirsThatBelongToAnExistingApp() {
        File real = dir("vue_project_465377089437237248", 100);
        when(appService.getById(465377089437237248L)).thenReturn(new App());

        assertEquals(0, cleaner.cleanup(TTL));

        assertTrue(real.exists());
        verifyNoInteractions(memoryStore);
    }

    @Test
    void keepsYoungDirsEvenWithoutApp() {
        // 工作流刚生成、还没超过保留时间的目录不能删，用户可能正在预览
        File young = dir("html_2107820259536330752", 1);
        when(appService.getById(anyLong())).thenReturn(null);

        assertEquals(0, cleaner.cleanup(TTL));

        assertTrue(young.exists());
        // 年轻目录甚至不应该去查数据库
        verifyNoInteractions(appService);
    }

    @Test
    void ignoresDirsAndFilesWithUnexpectedNames() throws Exception {
        File[] untouched = {
                dir("node_modules", 100),
                dir("html_abc", 100),
                dir("html_", 100),
                dir("other_123", 100),
                dir("html_123_backup", 100),
                dir("xhtml_123", 100),
                dir("HTML_123", 100),
        };
        File plainFile = new File(root, "html_999");
        Files.writeString(plainFile.toPath(), "i am a file, not a dir");
        plainFile.setLastModified(System.currentTimeMillis() - Duration.ofHours(100).toMillis());
        when(appService.getById(anyLong())).thenReturn(null);

        assertEquals(0, cleaner.cleanup(TTL));

        for (File f : untouched) {
            assertTrue(f.exists(), f.getName() + " 不应被清理");
        }
        assertTrue(plainFile.exists());
        verifyNoInteractions(appService, memoryStore);
    }

    @Test
    void doesNotFollowOrDeleteSymlinks(@TempDir File outside) throws Exception {
        FileUtil.writeUtf8String("precious", new File(outside, "data.txt"));
        File link = new File(root, "html_777");
        try {
            Files.createSymbolicLink(link.toPath(), outside.toPath());
        } catch (UnsupportedOperationException | java.io.IOException e) {
            // 当前系统不支持软链接，跳过
            return;
        }
        when(appService.getById(anyLong())).thenReturn(null);

        cleaner.cleanup(Duration.ZERO);

        assertTrue(new File(outside, "data.txt").exists(), "软链接指向的外部目录绝不能被删除");
        assertTrue(Files.isSymbolicLink(link.toPath()));
    }

    @Test
    void abortsWithoutDeletingAnythingWhenDatabaseFails() {
        File first = dir("html_1000", 100);
        File second = dir("html_2000", 100);
        // 数据库出错不能当作“应用不存在”，否则会把真实应用的代码全部删掉
        when(appService.getById(anyLong())).thenThrow(new RuntimeException("db down"));

        assertThrows(RuntimeException.class, () -> cleaner.cleanup(TTL));

        assertTrue(first.exists() && second.exists());
        verifyNoInteractions(memoryStore);
    }

    @Test
    void missingRootDirIsNotAnError() {
        ReflectionTestUtils.setField(cleaner, "rootDir", new File(root, "not-exist").getAbsolutePath());
        assertEquals(0, cleaner.cleanup(TTL));
    }

    @Test
    void redisFailureDoesNotStopTheCleanup() {
        File a = dir("html_1000", 100);
        File b = dir("html_2000", 100);
        when(appService.getById(anyLong())).thenReturn(null);
        doThrow(new RuntimeException("redis down")).when(memoryStore).deleteMessages(any());

        assertEquals(2, cleaner.cleanup(TTL));

        assertFalse(a.exists() || b.exists());
    }

    @Test
    void scheduledJobSwallowsErrorsAndRespectsEnabledFlag() {
        dir("html_1000", 100);
        when(appService.getById(anyLong())).thenThrow(new RuntimeException("db down"));
        // 开启时：异常被吞掉，不会让定时线程挂掉
        ReflectionTestUtils.setField(cleaner, "enabled", true);
        ReflectionTestUtils.setField(cleaner, "ttlHours", 24L);
        assertDoesNotThrow(cleaner::scheduledCleanup);
        // 关闭时：完全不执行
        reset(appService);
        ReflectionTestUtils.setField(cleaner, "enabled", false);
        cleaner.scheduledCleanup();
        verifyNoInteractions(appService);
    }
}
