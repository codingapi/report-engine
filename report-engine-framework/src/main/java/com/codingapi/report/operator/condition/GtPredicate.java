package com.codingapi.report.operator.condition;

import com.codingapi.report.operator.Values;

/** 大于算子：{@code left > right}，跨类型按数值优先比较（见 {@link Values#compare}）。 */
public class GtPredicate implements ConditionPredicate {

    @Override
    public boolean supports(CompareOperator operator) {
        return operator == CompareOperator.GT;
    }

    @Override
    public boolean test(Object left, Object right) {
        return Values.compare(left, right) > 0;
    }
}
