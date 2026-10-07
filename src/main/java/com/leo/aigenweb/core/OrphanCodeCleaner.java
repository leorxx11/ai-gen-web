package com.leo.aigenweb.core;

import cn.hutool.core.io.FileUtil;
import com.leo.aigenweb.constant.AppConstant;
import com.leo.aigenweb.service.AppService;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 孤儿代码目录清理器。
 * <p>
 * 代码生成目录 {@code tmp/code_output/<类型>_<appId>} 正常都对应一条应用记录。但工作流每次执行都会分配一个
 * 不对应任何应用的 appId，被删除的应用、旧的测试也会留下目录，这些目录不会被任何流程回收。
 * <p>
 * 为避免误删，只清理同时满足以下条件的目录：名称严格匹配、数据库里没有对应应用、超过保留时间（避开正在生成的），
 * 并且不跟随软链接。逻辑删除（isDelete=1）的应用仍视为存在，其目录保留。查询数据库出错时整轮放弃，不做任何删除。
 */
@Slf4j
@Component
public class OrphanCodeCleaner {

    /**
     * 只匹配生成目录的命名：类型_appId
     */
    private static final Pattern GENERATED_DIR = Pattern.compile("^(html|multi_file|vue_project)_(\\d+)$");

    @Resource
    private AppService appService;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Value("${workflow.cleanup.enabled:true}")
    private boolean enabled;

    /**
     * 目录保留时间（小时）。目录的修改时间只反映其直接子项的变化，所以不要设得太短，至少要长于一次生成的耗时
     */
    @Value("${workflow.cleanup.ttl-hours:24}")
    private long ttlHours;

    /**
     * 扫描的根目录，默认是应用生成目录；可在测试中替换
     */
    private String rootDir = AppConstant.CODE_OUTPUT_ROOT_DIR;

    @Scheduled(cron = "${workflow.cleanup.cron:0 15 * * * ?}")
    public void scheduledCleanup() {
        if (!enabled) {
            return;
        }
        try {
            int deleted = cleanup(Duration.ofHours(ttlHours));
            if (deleted > 0) {
                log.info("孤儿代码目录清理完成，共删除 {} 个", deleted);
            }
        } catch (Exception e) {
            log.error("孤儿代码目录清理失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 执行一次清理，返回删除的目录数
     *
     * @param ttl 目录保留时间，修改时间距今不足该时长的不会被删除
     * @throws RuntimeException 查询数据库失败时抛出，此时不会删除任何目录
     */
    public int cleanup(Duration ttl) {
        File[] children = new File(rootDir).listFiles(File::isDirectory);
        if (children == null) {
            return 0;
        }
        long threshold = System.currentTimeMillis() - ttl.toMillis();
        int deleted = 0;
        for (File dir : children) {
            Matcher matcher = GENERATED_DIR.matcher(dir.getName());
            // 名称不匹配、软链接、未超过保留时间的都跳过
            if (!matcher.matches() || Files.isSymbolicLink(dir.toPath()) || dir.lastModified() > threshold) {
                continue;
            }
            long appId;
            try {
                appId = Long.parseLong(matcher.group(2));
            } catch (NumberFormatException e) {
                continue;
            }
            // 有对应应用记录的是正常目录；这里查询失败会直接抛出，整轮放弃。
            // 应用是逻辑删除的，查询时必须忽略逻辑删除：被“删除”的应用仍可能被恢复，其代码不能被清掉
            if (LogicDeleteManager.execWithoutLogicDelete(() -> appService.getById(appId)) != null) {
                continue;
            }
            if (FileUtil.del(dir)) {
                deleted++;
                log.info("已清理孤儿代码目录: {}", dir.getName());
            }
            // 对话记忆自带过期时间，这里顺手清掉，让残留尽早消失
            try {
                redisChatMemoryStore.deleteMessages(appId);
            } catch (Exception e) {
                log.warn("清理对话记忆失败 appId={}: {}", appId, e.getMessage());
            }
        }
        return deleted;
    }
}
