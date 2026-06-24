package com.codingapi.report.operator.condition;

import com.codingapi.report.operator.Values;

import java.util.Arrays;
import java.util.List;

/**
 * 范围算子：{@code field BETWEEN low AND high}，闭区间（Java 内存判定，非 SQL）。
 *
 * <p>右值为逗号分隔的字符串 {@code "low,high"}，取前两个非空段作为下/上界，
 * 用 {@link Values#compare} 判定 {@code low ≤ left ≤ high}（数值优先，否则字典序）。
 */
public class BetweenPredicate implements ConditionPredicate {

    @Override
    public boolean supports(CompareOperator operator) {
        return operator == CompareOperator.BETWEEN;
    }

    @Override
    public boolean test(Object left, Object right) {
        if (left == null || right == null) {
            return false;
        }
        List<String> bounds = Arrays.stream(String.valueOf(right).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (bounds.size() < 2) {
            return false;
        }
        return Values.compare(left, bounds.get(0)) >= 0 && Values.compare(left, bounds.get(1)) <= 0;
    }
}
