package com.codingapi.report.operator.condition;

/** 不为空算子：{@code left IS NOT NULL}，{@link IsNullPredicate} 的取反。 */
public class IsNotNullPredicate implements ConditionPredicate {

    @Override
    public boolean supports(CompareOperator operator) {
        return operator == CompareOperator.IS_NOT_NULL;
    }

    @Override
    public boolean test(Object left, Object right) {
        return left != null && !String.valueOf(left).isEmpty();
    }
}
