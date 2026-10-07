package com.leo.aigenweb.ai;

import com.leo.aigenweb.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
@SpringBootTest
class AiCodeGenTypeRoutingServiceTest {

    @Resource
    private AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService;

    @Test
    void testRouteCodeGenType() {
        String simple = "做一个简单的个人介绍页面";
        CodeGenTypeEnum result = aiCodeGenTypeRoutingService.routeCodeGenType(simple);
        log.info("用户需求: {} -> {}", simple, result.getValue());
        assertNotNull(result);
        assertEquals(CodeGenTypeEnum.HTML, result);

        String multiPage = "做一个公司官网，需要首页、关于我们、联系我们三个页面";
        result = aiCodeGenTypeRoutingService.routeCodeGenType(multiPage);
        log.info("用户需求: {} -> {}", multiPage, result.getValue());
        assertNotNull(result);
        assertEquals(CodeGenTypeEnum.MULTI_FILE, result);

        String complex = "做一个电商管理系统，包含用户管理、商品管理、订单管理，需要路由和状态管理";
        result = aiCodeGenTypeRoutingService.routeCodeGenType(complex);
        log.info("用户需求: {} -> {}", complex, result.getValue());
        assertNotNull(result);
        assertEquals(CodeGenTypeEnum.VUE_PROJECT, result);
    }
}
