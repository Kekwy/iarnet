package com.kekwy.iarnet.actor;

/**
 * 算子语义类型，决定 UDF 返回值如何路由到下游。
 * <ul>
 *   <li>MAP — 返回值直接作为新数据发往下游</li>
 *   <li>FILTER — 返回 boolean，为 true 时将<b>原始输入</b>发往下游</li>
 *   <li>FLAT_MAP — 返回 Iterable，逐元素展开后分别发往下游</li>
 * </ul>
 */
public enum OperatorSemantics {

    MAP,
    FILTER,
    FLAT_MAP;

    /**
     * 从环境变量 {@code IARNET_OPERATOR_KIND} 的值解析。
     * 对应 proto 枚举名：OPERATOR_MAP / OPERATOR_FILTER / OPERATOR_FLAT_MAP。
     */
    public static OperatorSemantics fromEnvString(String s) {
        if (s == null || s.isBlank()) return null;
        return switch (s.trim().toUpperCase()) {
            case "OPERATOR_MAP", "MAP" -> MAP;
            case "OPERATOR_FILTER", "FILTER" -> FILTER;
            case "OPERATOR_FLAT_MAP", "FLAT_MAP", "FLATMAP" -> FLAT_MAP;
            default -> null;
        };
    }

    /**
     * 根据 UDF 方法的返回值类型推断算子语义。
     * <ul>
     *   <li>boolean / Boolean → FILTER</li>
     *   <li>Iterable 及其子类 → FLAT_MAP</li>
     *   <li>其他 → MAP</li>
     * </ul>
     */
    public static OperatorSemantics inferFromReturnType(Class<?> returnType) {
        if (returnType == boolean.class || returnType == Boolean.class) {
            return FILTER;
        }
        if (Iterable.class.isAssignableFrom(returnType)) {
            return FLAT_MAP;
        }
        return MAP;
    }
}
