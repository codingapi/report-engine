package com.codingapi.report.operator.condition;

/** 为空算子：{@code left IS NULL}，null 或空字符串视为空（Java 内存判定）。 */
public class IsNullPredicate implements ConditionPredicate {

    @Override
    public boolean supports(CompareOperator operator) {
        return operator == CompareOperator.IS_NULL;
    }

    @Override
    public boolean test(Object left, Object right) {
        return left == null || String.valueOf(left).isEmpty();
    }
}
