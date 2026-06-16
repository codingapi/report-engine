package com.codingapi.report.operator.condition;

import com.codingapi.report.operator.Values;

/** 大于等于算子：{@code left ≥ right}。 */
public class GePredicate implements ConditionPredicate {

    @Override
    public boolean supports(CompareOperator operator) {
        return operator == CompareOperator.GE;
    }

    @Override
    public boolean test(Object left, Object right) {
        return Values.compare(left, right) >= 0;
    }
}
