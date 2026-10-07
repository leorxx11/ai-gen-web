package com.leo.aigenweb.ratelimit.enums;

/**
 * 限流类型
 */
public enum RateLimitType {

    /**
     * 接口级别：所有用户共享同一个额度
     */
    API,

    /**
     * 用户级别：按登录用户 ID，未登录时退化为按 IP
     */
    USER,

    /**
     * IP 级别：按客户端 IP
     */
    IP
}
