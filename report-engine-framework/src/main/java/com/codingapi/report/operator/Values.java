package com.codingapi.report.operator;

/**
 * 值运算的底层工具：数值强转与跨类型比较语义，被聚合算子（{@code operator.aggregation}） 和条件算子（{@code operator.condition}）共享。
 *
 * <p>放在 {@code operator} 父包下而非某个子包，让两类算子都能复用同一套数值语义， 又互不依赖对方。
 *
 * <h3>跨类型比较</h3>
 *
 * <p>报表数据来自 CSV/DB/API，同一字段在不同源里可能是字符串 {@code "8000"} 或数字 {@code 8000.0}。 这里统一"能转 double
 * 就按数值比，否则按字符串比"，使 {@code "1" == 1}、{@code "3.0" == 3} 成立。
 */
public final class Values {

    private Values() {}

    /** 安全转 Double：Number 直接取值，字符串尝试解析，null 或解析失败返回 null。 */
    public static Double toDouble(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 相等比较：数值优先，两端都能转 double 时按数值比，否则按字符串比。
     *
     * <p>这样设计的泛化能力强：
     *
     * <ul>
     *   <li>{@code 1 == "1"} → 两端都能转 1.0 → true
     *   <li>{@code "1" == 1} → 两端都能转 1.0 → true
     *   <li>{@code "abc" == "abc"} → 不能转 double → 字符串比较 → true
     *   <li>{@code 10.0 == 10} → 两端都能转 10.0 → true（避免格式差异）
     * </ul>
     *
     * <p>CSV 数据源会将 NUMBER 字段转为 Double（如 "10" → 10.0）， 而用户输入可能是 Integer（如 10），数值比较能正确处理这种差异。
     */
    public static boolean equals(Object l, Object r) {
        Double dl = toDouble(l);
        Double dr = toDouble(r);
        if (dl != null && dr != null) {
            return dl.doubleValue() == dr.doubleValue();
        }
        return String.valueOf(l).equals(String.valueOf(r));
    }

    /** 大小比较：两端都能转 double 时按数值比，否则按字符串字典序比。 */
    public static int compare(Object l, Object r) {
        Double dl = toDouble(l);
        Double dr = toDouble(r);
        if (dl != null && dr != null) {
            return Double.compare(dl, dr);
        }
        return String.valueOf(l).compareTo(String.valueOf(r));
    }
}
