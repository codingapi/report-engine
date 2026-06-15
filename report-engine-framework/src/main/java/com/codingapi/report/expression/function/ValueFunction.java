package com.codingapi.report.expression.function;

import java.util.List;

/**
 * 表达式函数（SPI）：一个具名函数对应一个实现，处理已求值的参数列表。
 *
 * <p>{@link Value.FunctionCall} 的实际行为由这里提供——"支持哪些函数"是预先约定（注册）的。
 * 与 {@code Aggregator}/{@code ConditionPredicate} 同一套范式：{@code supports(name)} 选中 +
 * {@link Functions} 注册表分发。新增函数（round/concat/if…）= 实现本接口并登记，零改动接入。
 */
public interface ValueFunction {

    /** 是否支持该函数名。 */
    boolean supports(String name);

    /**
     * 执行函数。
     *
     * @param args 已求值的参数（由 {@code FunctionCallEvaluator} 先对各参数求值后传入）
     */
    Object apply(List<Object> args);
}
