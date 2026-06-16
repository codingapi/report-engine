package com.codingapi.report.operator.condition;

import com.codingapi.report.operator.Values;

/** 小于算子：{@code left < right}。 */
public class LtPredicate implements ConditionPredicate {

    @Override
    public boolean supports(CompareOperator operator) {
        return operator == CompareOperator.LT;
    }

    @Override
    public boolean test(Object left, Object right) {
        return Values.compare(left, right) < 0;
    }
}
