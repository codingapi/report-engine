package com.codingapi.report.expression;

/**
 * 值求值策略（SPI）：一种 {@link Value} 节点对应一个实现，<b>唯一职责是把该节点算成一个值</b>。
 *
 * <p>与 {@code DataExtractor}/{@code Aggregator}/{@code ConditionPredicate} 同一套扩展范式： {@code
 * supports()} 选中 + 注册表分发。节点求值若需递归子表达式，通过传入的 {@link ExpressionEngine} 回调。
 *
 * @see ExpressionEngine
 */
public interface ValueEvaluator {

    /** 是否处理该节点。{@link ExpressionEngine} 遍历注册表用此方法选中实现。 */
    boolean supports(Value value);

    /**
     * 求值。
     *
     * @param value 待求值节点（已由 {@link #supports} 确认类型）
     * @param ctx 求值上下文
     * @param engine 引擎回调，用于递归求值子表达式
     */
    Object eval(Value value, EvalContext ctx, ExpressionEngine engine);
}
