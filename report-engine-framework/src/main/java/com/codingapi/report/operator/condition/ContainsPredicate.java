package com.codingapi.report.operator.condition;

/** 包含算子：{@code left 包含 right}，按字符串子串匹配（Java 内存，非 SQL LIKE）。 */
public class ContainsPredicate implements ConditionPredicate {

    @Override
    public boolean supports(CompareOperator operator) {
        return operator == CompareOperator.CONTAINS;
    }

    @Override
    public boolean test(Object left, Object right) {
        if (left == null || right == null) {
            return false;
        }
        return String.valueOf(left).contains(String.valueOf(right));
    }
}
