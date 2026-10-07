package com.leo.aigenweb.service;

import cn.hutool.core.io.FileUtil;
import com.leo.aigenweb.exception.BusinessException;
import com.leo.aigenweb.service.impl.ProjectDownloadServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class ProjectDownloadServiceTest {

    private final ProjectDownloadService service = new ProjectDownloadServiceImpl();

    @Test
    void zipShouldContainSourceAndSkipIgnoredFiles(@TempDir File dir) throws Exception {
        FileUtil.writeUtf8String("<html></html>", new File(dir, "index.html"));
        FileUtil.writeUtf8String("export {}", new File(dir, "src/main.js"));
        FileUtil.writeUtf8String("x", new File(dir, "node_modules/vue/index.js"));
        FileUtil.writeUtf8String("x", new File(dir, "dist/index.html"));
        FileUtil.writeUtf8String("SECRET=1", new File(dir, ".env"));
        FileUtil.writeUtf8String("log", new File(dir, "debug.log"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        service.downloadProjectAsZip(dir.getAbsolutePath(), "123", response);

        assertEquals("application/zip", response.getContentType());
        assertEquals("attachment; filename=\"123.zip\"", response.getHeader("Content-Disposition"));

        Set<String> names = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                names.add(e.getName());
            }
        }
        assertTrue(names.contains("index.html"), names.toString());
        assertTrue(names.contains("src/main.js"), names.toString());
        assertTrue(names.stream().noneMatch(n -> n.contains("node_modules") || n.startsWith("dist")
                || n.equals(".env") || n.endsWith(".log")), names.toString());
    }

    @Test
    void shouldRejectMissingDirectory() {
        assertThrows(BusinessException.class, () ->
                service.downloadProjectAsZip("/no/such/dir", "1", new MockHttpServletResponse()));
    }
}
