package com.codingapi.report.operator.condition;

import java.util.List;

/**
 * 条件算子注册表：内置全部 {@link ConditionPredicate} 实现，按 {@link CompareOperator} 选中并求值。
 *
 * <h3>扩展方式</h3>
 * <p>新增一个比较算子（如 REGEX/BETWEEN）：实现 {@link ConditionPredicate}，
 * 在 {@link #REGISTRY} 列表里登记一行即可，无需改动 {@code Operators} 或任何调用方。
 *
 * <h3>与旧实现的区别</h3>
 * <p>旧的 {@code switch} 对未实现的算子 {@code default -> true}（静默放行，导致过滤失效）。
 * 这里改为：没有任何策略支持时<b>抛异常</b>，让"用了未实现的算子"在运行时立刻暴露，而不是悄悄出错。
 */
public final class ConditionPredicates {

    /** 内置算子实现。新增算子在此登记。 */
    private static final List<ConditionPredicate> REGISTRY = List.of(
            new EqPredicate(),
            new NePredicate(),
            new GtPredicate(),
            new GePredicate(),
            new LtPredicate(),
            new LePredicate(),
            new ContainsPredicate(),
            new NotContainsPredicate(),
            new InPredicate(),
            new NotInPredicate(),
            new IsNullPredicate(),
            new IsNotNullPredicate()
    );

    private ConditionPredicates() {
    }

    /**
     * 求值 {@code left op right}。
     *
     * @throws IllegalStateException 当没有策略支持该算子（即该算子尚未实现）
     */
    public static boolean test(CompareOperator operator, Object left, Object right) {
        for (ConditionPredicate p : REGISTRY) {
            if (p.supports(operator)) {
                return p.test(left, right);
            }
        }
        throw new IllegalStateException("不支持的比较算子: " + operator);
    }
}
