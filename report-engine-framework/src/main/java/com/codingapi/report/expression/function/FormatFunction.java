package com.codingapi.report.expression.function;

import com.codingapi.report.operator.Values;

import java.text.DecimalFormat;
import java.util.List;

/**
 * 数值格式化函数 {@code format(value, pattern)}：按 {@link DecimalFormat} 模式格式化数值。
 * <p>例：{@code format(1234.5, "#,##0.00")} → {@code "1,234.50"}。
 * 非数值原样返回字符串，null 返回空串。
 */
public class FormatFunction implements ValueFunction {

    @Override
    public boolean supports(String name) {
        return "format".equals(name);
    }

    @Override
    public Object apply(List<Object> args) {
        Object value = args.get(0);
        String pattern = String.valueOf(args.get(1));
        if (value == null) {
            return "";
        }
        Double d = Values.toDouble(value);
        return d == null ? String.valueOf(value) : new DecimalFormat(pattern).format(d);
    }
}
