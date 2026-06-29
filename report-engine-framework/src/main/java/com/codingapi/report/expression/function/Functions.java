package com.codingapi.report.expression.function;

import java.util.List;

/**
 * 函数注册表：内置全部 {@link ValueFunction} 实现，按函数名选中并调用。
 *
 * <h3>扩展方式</h3>
 *
 * <p>新增函数（round/concat/if…）：实现 {@link ValueFunction}，在 {@link #REGISTRY} 登记一行即可， 无需改动 {@code
 * ExpressionEngine} 或调用方。未注册的函数名会显式抛异常。
 */
public final class Functions {

    /** 内置函数。新增函数在此登记。 */
    private static final List<ValueFunction> REGISTRY =
            List.of(
                    new FormatFunction(),
                    new DateFunction(),
                    new RoundFunction(),
                    new ConcatFunction(),
                    new IfFunction(),
                    new MapFunction());

    private Functions() {}

    /** 列出所有已注册函数的元信息（供前端表达式构建器枚举）。 */
    public static List<FunctionMeta> list() {
        return REGISTRY.stream().map(ValueFunction::meta).toList();
    }

    /**
     * 调用函数。
     *
     * @param name 函数名
     * @param args 已求值的参数
     * @throws IllegalStateException 当没有函数支持该名字
     */
    public static Object call(String name, List<Object> args) {
        for (ValueFunction f : REGISTRY) {
            if (f.supports(name)) {
                return f.apply(args);
            }
        }
        throw new IllegalStateException("不支持的函数: " + name);
    }
}
