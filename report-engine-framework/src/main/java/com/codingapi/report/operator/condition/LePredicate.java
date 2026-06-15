package com.codingapi.report.operator.condition;

import com.codingapi.report.operator.Values;

/** 小于等于算子：{@code left ≤ right}。 */
public class LePredicate implements ConditionPredicate {

    @Override
    public boolean supports(CompareOperator operator) {
        return operator == CompareOperator.LE;
    }

    @Override
    public boolean test(Object left, Object right) {
        return Values.compare(left, right) <= 0;
    }
}
