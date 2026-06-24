package com.codingapi.report.operator.condition;

import com.codingapi.report.operator.Values;
import java.util.Arrays;

/** 不包含于列表算子：{@code left NOT IN (v1, v2, ...)}，{@link InPredicate} 的取反。 */
public class NotInPredicate implements ConditionPredicate {

    @Override
    public boolean supports(CompareOperator operator) {
        return operator == CompareOperator.NOT_IN;
    }

    @Override
    public boolean test(Object left, Object right) {
        if (left == null) {
            return true;
        }
        if (right == null) {
            return true;
        }
        String rightStr = String.valueOf(right);
        if (rightStr.isEmpty()) {
            return true;
        }
        return Arrays.stream(rightStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .noneMatch(item -> Values.equals(left, item));
    }
}
