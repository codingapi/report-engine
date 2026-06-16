package com.codingapi.report.operator.condition;

import com.codingapi.report.operator.Values;

/** 等于算子：{@code left = right}，跨类型按数值优先比较（见 {@link Values#equals}）。 */
public class EqPredicate implements ConditionPredicate {

    @Override
    public boolean supports(CompareOperator operator) {
        return operator == CompareOperator.EQ;
    }

    @Override
    public boolean test(Object left, Object right) {
        return Values.equals(left, right);
    }
}
