package com.codingapi.report.operator.condition;

import com.codingapi.report.operator.Values;

/** 不等于算子：{@code left ≠ right}。 */
public class NePredicate implements ConditionPredicate {

    @Override
    public boolean supports(CompareOperator operator) {
        return operator == CompareOperator.NE;
    }

    @Override
    public boolean test(Object left, Object right) {
        return !Values.equals(left, right);
    }
}
