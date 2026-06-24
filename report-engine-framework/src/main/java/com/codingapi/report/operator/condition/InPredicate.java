package com.codingapi.report.operator.condition;

import com.codingapi.report.operator.Values;
import java.util.Arrays;

/**
 * 包含于列表算子：{@code left IN (v1, v2, ...)}，右值为逗号分隔的字符串（Java 内存解析，非 SQL）。
 *
 * <p>逐项用 {@link Values#equals} 比对，兼容 {@code "8000" == 8000.0} 的跨类型相等。
 */
public class InPredicate implements ConditionPredicate {

    @Override
    public boolean supports(CompareOperator operator) {
        return operator == CompareOperator.IN;
    }

    @Override
    public boolean test(Object left, Object right) {
        if (left == null || right == null) {
            return false;
        }
        String rightStr = String.valueOf(right);
        if (rightStr.isEmpty()) {
            return false;
        }
        return Arrays.stream(rightStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(item -> Values.equals(left, item));
    }
}
