package com.leo.aigenweb.ratelimit.aspect;

import com.leo.aigenweb.exception.BusinessException;
import com.leo.aigenweb.exception.ErrorCode;
import com.leo.aigenweb.model.entity.User;
import com.leo.aigenweb.ratelimit.annotation.RateLimit;
import com.leo.aigenweb.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * 限流切面：基于 Redisson 的分布式令牌桶
 */
@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private UserService userService;

    @Before("@annotation(rateLimit)")
    public void doBefore(JoinPoint point, RateLimit rateLimit) {
        String key = generateRateLimitKey(point, rateLimit);
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        // 限流器已存在时 trySetRate 不会覆盖；先设置规则，限流器 key 才存在，再设置过期时间，避免 Redis 里的 key 永不过期
        rateLimiter.trySetRate(RateType.OVERALL, rateLimit.rate(), rateLimit.rateInterval(), RateIntervalUnit.SECONDS);
        rateLimiter.expire(Duration.ofHours(1));
        if (!rateLimiter.tryAcquire(1)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, rateLimit.message());
        }
    }

    private String generateRateLimitKey(JoinPoint point, RateLimit rateLimit) {
        StringBuilder keyBuilder = new StringBuilder("rate_limit:");
        if (!rateLimit.key().isEmpty()) {
            keyBuilder.append(rateLimit.key()).append(":");
        }
        switch (rateLimit.limitType()) {
            case API -> {
                Method method = ((MethodSignature) point.getSignature()).getMethod();
                keyBuilder.append("api:").append(method.getDeclaringClass().getSimpleName())
                        .append(".").append(method.getName());
            }
            case USER -> {
                ServletRequestAttributes attributes = currentRequestAttributes();
                if (attributes == null) {
                    keyBuilder.append("ip:unknown");
                } else {
                    try {
                        User loginUser = userService.getLoginUser(attributes.getRequest());
                        keyBuilder.append("user:").append(loginUser.getId());
                    } catch (BusinessException e) {
                        // 未登录：退化为按 IP 限流
                        keyBuilder.append("ip:").append(getClientIP(attributes.getRequest()));
                    }
                }
            }
            case IP -> {
                ServletRequestAttributes attributes = currentRequestAttributes();
                keyBuilder.append("ip:").append(attributes == null ? "unknown" : getClientIP(attributes.getRequest()));
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的限流类型");
        }
        return keyBuilder.toString();
    }

    private ServletRequestAttributes currentRequestAttributes() {
        return (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    }

    /**
     * 获取客户端 IP。这里只用 getRemoteAddr：直接读 X-Forwarded-For 请求头可以被客户端随意伪造来绕过限流，
     * 部署在反向代理之后时由 Tomcat 的 remoteip 配置（见 application.yaml）还原真实 IP。
     */
    private String getClientIP(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return ip != null ? ip : "unknown";
    }
}
