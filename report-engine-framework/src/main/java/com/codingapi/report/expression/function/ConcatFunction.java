package com.codingapi.report.expression.function;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 拼接函数 {@code concat(v1, v2, ...)}：将多个值按顺序拼接为字符串，null 跳过。
 * <p>例：{@code concat(d.first, d.last)} → {@code "张三丰"}；{@code concat("a", "b")} → {@code "ab"}。
 */
public class ConcatFunction implements ValueFunction {

    @Override
    public boolean supports(String name) {
        return "concat".equals(name);
    }

    @Override
    public FunctionMeta meta() {
        return new FunctionMeta("concat", "拼接",
                List.of("值1", "值2", "..."),
                "拼接多个值为字符串，例：concat(d.first, d.last)");
    }

    @Override
    public Object apply(List<Object> args) {
        return args.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining());
    }
}
