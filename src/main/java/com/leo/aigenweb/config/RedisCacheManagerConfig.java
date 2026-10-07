package com.leo.aigenweb.config;

import jakarta.annotation.Resource;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 缓存管理器。
 * <p>
 * value 使用默认的 JDK 序列化：用 JSON 序列化的话，Redis 里没有保存 Java 类型信息，
 * 反序列化 Page、BaseResponse 这类带泛型的对象会失败。缓存的对象需要实现 Serializable。
 */
@Configuration
public class RedisCacheManagerConfig {

    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    @Bean
    public CacheManager cacheManager() {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues()
                // key 用字符串序列化，Redis 里可读
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()));
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                // 主页精选应用由管理员手动设置，更新少，缓存 5 分钟
                .withCacheConfiguration("good_app_page", defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .build();
    }
}
