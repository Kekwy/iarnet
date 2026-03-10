package com.kekwy.iarnet.sdk.util;

import java.util.UUID;

/**
 * ID 生成工具。
 * <p>
 * 用于生成工作流图、节点等实体的唯一标识。
 */
public final class IDUtil {

    private IDUtil() {
        // 工具类，禁止实例化
    }

    /**
     * 生成无连字符的 32 位 UUID 字符串。
     *
     * @return 形如 {@code a1b2c3d4e5f6...} 的字符串
     */
    public static String genUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成带前缀的 UUID 字符串，格式为 {@code prefix-uuidWithoutHyphen}。
     *
     * @param prefix 前缀，如节点名
     * @return 形如 {@code prefix-a1b2c3d4e5f6...} 的字符串
     */
    public static String genUUIDWith(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }
}
