package com.codingapi.report.operator.condition;

/** 不包含算子：{@code left 不包含 right}，{@link ContainsPredicate} 的取反。 */
public class NotContainsPredicate implements ConditionPredicate {

    @Override
    public boolean supports(CompareOperator operator) {
        return operator == CompareOperator.NOT_CONTAINS;
    }

    @Override
    public boolean test(Object left, Object right) {
        if (left == null || right == null) {
            return true;
        }
        return !String.valueOf(left).contains(String.valueOf(right));
    }
}
