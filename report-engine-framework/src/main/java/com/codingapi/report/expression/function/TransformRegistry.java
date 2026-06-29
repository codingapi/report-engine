package com.codingapi.report.expression.function;

import java.util.Map;

/**
 * 渲染期转换项注册表（ThreadLocal 作用域）。
 *
 * <p>{@code map(字段, 转换项id)} 函数需查转换项定义，但 {@link ValueFunction#apply} 不带上下文。 渲染入口处由 DataModel 的转换项
 * {@code 转换项id → (编码 → 呈现)} 装入本注册表，{@link MapFunction} 读取， 渲染结束清理，避免跨渲染串台。单次渲染在同一线程内完成，故用
 * ThreadLocal 隔离。
 */
public final class TransformRegistry {

    private static final ThreadLocal<Map<String, Map<String, String>>> HOLDER = new ThreadLocal<>();

    private TransformRegistry() {}

    /** 装入本次渲染的转换项映射：{@code 转换项id → (编码 → 呈现)}。 */
    public static void set(Map<String, Map<String, String>> mappings) {
        HOLDER.set(mappings);
    }

    /** 按转换项 id 查编码映射；无则返回 null。 */
    public static Map<String, String> get(String transformId) {
        Map<String, Map<String, String>> all = HOLDER.get();
        return all == null ? null : all.get(transformId);
    }

    /** 渲染结束清理，防止 ThreadLocal 泄漏到下一次渲染。 */
    public static void clear() {
        HOLDER.remove();
    }
}
