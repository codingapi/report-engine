package com.codingapi.report.expression.function;

import com.codingapi.report.operator.Values;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 四舍五入函数 {@code round(value[, digits])}：对数值按指定小数位四舍五入。
 *
 * <p>例：{@code round(3.14159, 2)} → {@code 3.14}；{@code round(3.5)} → {@code 4.0}。 非数值原样返回字符串，null
 * 返回 null，digits 缺省为 0。
 */
public class RoundFunction implements ValueFunction {

    @Override
    public boolean supports(String name) {
        return "round".equals(name);
    }

    @Override
    public FunctionMeta meta() {
        return new FunctionMeta(
                "round", "四舍五入", List.of("数值", "小数位"), "对数值四舍五入到指定小数位，例：round(value, 2)");
    }

    @Override
    public Object apply(List<Object> args) {
        if (args.isEmpty()) {
            return null;
        }
        Object value = args.get(0);
        if (value == null) {
            return null;
        }
        Double d = Values.toDouble(value);
        if (d == null) {
            return String.valueOf(value);
        }
        int digits = args.size() > 1 ? toInt(args.get(1)) : 0;
        return BigDecimal.valueOf(d).setScale(digits, RoundingMode.HALF_UP).doubleValue();
    }

    private static int toInt(Object o) {
        Double dd = Values.toDouble(o);
        return dd == null ? 0 : dd.intValue();
    }
}
