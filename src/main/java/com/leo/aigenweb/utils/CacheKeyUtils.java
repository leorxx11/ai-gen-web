package com.leo.aigenweb.utils;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;

/**
 * 缓存 key 生成工具类
 */
public class CacheKeyUtils {

    private CacheKeyUtils() {
    }

    /**
     * 根据对象生成缓存 key（JSON + MD5）：相同内容的对象得到相同的 key，且长度固定
     *
     * @param obj 要生成 key 的对象
     * @return MD5 哈希后的缓存 key
     */
    public static String generateKey(Object obj) {
        if (obj == null) {
            return DigestUtil.md5Hex("null");
        }
        return DigestUtil.md5Hex(JSONUtil.toJsonStr(obj));
    }
}
