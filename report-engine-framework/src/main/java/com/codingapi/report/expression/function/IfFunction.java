package com.codingapi.report.expression.function;

import com.codingapi.report.operator.Values;

import java.util.List;

/**
 * 条件取值函数 {@code if(cond, trueVal, falseVal)}：条件成立返回真值，否则返回假值。
 * <p>例：{@code if(d.flag, '是', '否')}。
 *
 * <h3>真值判定（Java 内存，非 SQL）</h3>
 * <ul>
 *   <li>Boolean → 本身</li>
 *   <li>Number → {@code != 0}</li>
 *   <li>String → 非空且非 {@code "false"}/{@code "0"}（忽略大小写）</li>
 *   <li>null → false</li>
 * </ul>
 */
public class IfFunction implements ValueFunction {

    @Override
    public boolean supports(String name) {
        return "if".equals(name);
    }

    @Override
    public FunctionMeta meta() {
        return new FunctionMeta("if", "条件取值",
                List.of("条件", "真值", "假值"),
                "条件成立返回真值否则假值，例：if(d.flag, '是', '否')");
    }

    @Override
    public Object apply(List<Object> args) {
        if (args.size() < 3) {
            return null;
        }
        return truthy(args.get(0)) ? args.get(1) : args.get(2);
    }

    static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.doubleValue() != 0;
        }
        Double d = Values.toDouble(value);
        if (d != null) {
            return d != 0;
        }
        String s = String.valueOf(value);
        return !s.isEmpty() && !s.equalsIgnoreCase("false") && !"0".equals(s);
    }
}
