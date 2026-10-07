package com.leo.aigenweb.controller;

import com.leo.aigenweb.common.BaseResponse;
import com.leo.aigenweb.model.dto.app.AppQueryRequest;
import com.leo.aigenweb.model.vo.AppVO;
import com.leo.aigenweb.service.AppService;
import com.leo.aigenweb.utils.CacheKeyUtils;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 主页精选应用缓存（真实 Redis，真实序列化）
 */
@SpringBootTest
class GoodAppCacheTest {

    @Resource
    private AppController appController;

    @Resource
    private CacheManager cacheManager;

    @MockitoSpyBean
    private AppService appService;

    private Cache cache() {
        return cacheManager.getCache("good_app_page");
    }

    @BeforeEach
    @AfterEach
    void clear() {
        cache().clear();
        clearInvocations(appService);
    }

    private AppQueryRequest request(int pageNum, int pageSize) {
        AppQueryRequest request = new AppQueryRequest();
        request.setPageNum(pageNum);
        request.setPageSize(pageSize);
        return request;
    }

    @Test
    void secondCallIsServedFromRedis() {
        BaseResponse<Page<AppVO>> first = appController.listGoodAppVOByPage(request(1, 10));
        BaseResponse<Page<AppVO>> second = appController.listGoodAppVOByPage(request(1, 10));
        // 内容相同的两次请求只查一次数据库
        verify(appService, times(1)).page(any(Page.class), any(QueryWrapper.class));
        assertEquals(first.getCode(), second.getCode());
        assertEquals(first.getData().getTotalRow(), second.getData().getTotalRow());
        // 缓存里能按 key 取到，且 key 是 32 位 MD5
        String key = CacheKeyUtils.generateKey(request(1, 10));
        assertEquals(32, key.length());
        assertNotNull(cache().get(key), "应该写入了 Redis 缓存");
    }

    @Test
    void differentPagesUseDifferentKeys() {
        appController.listGoodAppVOByPage(request(1, 10));
        appController.listGoodAppVOByPage(request(2, 10));
        verify(appService, times(2)).page(any(Page.class), any(QueryWrapper.class));
        assertNotNull(cache().get(CacheKeyUtils.generateKey(request(1, 10))));
        assertNotNull(cache().get(CacheKeyUtils.generateKey(request(2, 10))));
    }

    @Test
    void pagesBeyondTenAreNotCached() {
        appController.listGoodAppVOByPage(request(11, 10));
        appController.listGoodAppVOByPage(request(11, 10));
        verify(appService, times(2)).page(any(Page.class), any(QueryWrapper.class));
        assertNull(cache().get(CacheKeyUtils.generateKey(request(11, 10))));
    }

    @Test
    void failedRequestsAreNotCached() {
        // 每页超过 20 条会抛业务异常，异常不应进入缓存
        assertThrows(Exception.class, () -> appController.listGoodAppVOByPage(request(1, 50)));
        assertNull(cache().get(CacheKeyUtils.generateKey(request(1, 50))));
    }

    @Test
    void cacheKeyIsStableAndContentBased() {
        assertEquals(CacheKeyUtils.generateKey(request(1, 10)), CacheKeyUtils.generateKey(request(1, 10)));
        assertNotEquals(CacheKeyUtils.generateKey(request(1, 10)), CacheKeyUtils.generateKey(request(1, 20)));
        assertEquals(CacheKeyUtils.generateKey(null), CacheKeyUtils.generateKey(null));
    }
}
