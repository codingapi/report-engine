package com.codingapi.report.expression.function;

import java.util.List;
import java.util.Map;

/**
 * 数据转换函数 {@code map(字段值, 转换项id)}：按转换项把原始编码映射为呈现文本。
 *
 * <p>例：性别字段 {@code map(d.sex, 'gender')}，转换项 {@code gender} 配 {@code 0→女,1→男}，渲染显示「男/女」。
 *
 * <h3>取数</h3>
 *
 * <p>转换项定义由渲染入口装入 {@link TransformRegistry}（渲染期 ThreadLocal），本函数按 {@code 转换项id} 查映射， 再用
 * {@code 字段值}（转字符串）查呈现。查不到映射或编码时回退原值，保证不丢数据。
 */
public class MapFunction implements ValueFunction {

    @Override
    public boolean supports(String name) {
        return "map".equals(name);
    }

    @Override
    public FunctionMeta meta() {
        return new FunctionMeta(
                "map",
                "数据转换",
                List.of("字段", "转换项id"),
                "按数据模型下的转换项把编码映射为呈现文本，例：map(d.sex, 'gender')");
    }

    @Override
    public Object apply(List<Object> args) {
        if (args.size() < 2) {
            return args.isEmpty() ? null : args.get(0);
        }
        Object raw = args.get(0);
        Object transformId = args.get(1);
        if (transformId == null) {
            return raw;
        }
        Map<String, String> mapping = TransformRegistry.get(String.valueOf(transformId));
        if (mapping == null || raw == null) {
            return raw;
        }
        // 编码按字符串比较：先直接匹配，再用归一形匹配。
        // 关键修复：DB 的 int 字段经渲染层 coerce 成 Double（如 0.0），直接 String.valueOf 得 "0.0"，
        // 与转换项配置的 "0" 不匹配 → 转换失效。故对整数值的数值类型归一为无小数形式再查。
        String key = String.valueOf(raw);
        String label = mapping.get(key);
        if (label == null) {
            String normalized = normalizeCode(raw);
            if (!normalized.equals(key)) {
                label = mapping.get(normalized);
            }
        }
        return label != null ? label : raw;
    }

    /** 数值编码归一：整数值的 Number（含 1.0/2L 等）输出无小数形式（"1"），便于与字典编码 "1" 匹配。 */
    private static String normalizeCode(Object raw) {
        if (raw instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                return Long.toString((long) d);
            }
        }
        return String.valueOf(raw);
    }
}
