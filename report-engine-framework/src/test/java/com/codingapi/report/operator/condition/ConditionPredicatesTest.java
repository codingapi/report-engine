package com.codingapi.report.operator.condition;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * 条件算子注册表测试：覆盖 CONTAINS/NOT_CONTAINS/IN/NOT_IN/IS_NULL/IS_NOT_NULL 六个算子， 验证 {@link
 * ConditionPredicates#test} 的策略分发与各算子语义（均在 Java 内存完成，不下推 SQL）。
 */
class ConditionPredicatesTest {

    // ─── CONTAINS / NOT_CONTAINS（子串包含）───────────────────────

    @Test
    void contains_substring_shouldMatch() {
        assertTrue(ConditionPredicates.test(CompareOperator.CONTAINS, "张三", "张"));
    }

    @Test
    void contains_notSubstring_shouldNotMatch() {
        assertFalse(ConditionPredicates.test(CompareOperator.CONTAINS, "李四", "张"));
    }

    @Test
    void contains_nullLeft_shouldBeFalse() {
        assertFalse(ConditionPredicates.test(CompareOperator.CONTAINS, null, "张"));
    }

    @Test
    void notContains_shouldBeInverse() {
        assertTrue(ConditionPredicates.test(CompareOperator.NOT_CONTAINS, "李四", "张"));
        assertFalse(ConditionPredicates.test(CompareOperator.NOT_CONTAINS, "张三", "张"));
    }

    // ─── IN / NOT_IN（逗号分隔列表，跨类型相等）───────────────────────

    @Test
    void in_stringList_shouldMatch() {
        assertTrue(ConditionPredicates.test(CompareOperator.IN, "研发", "研发,测试,运维"));
    }

    @Test
    void in_notInList_shouldNotMatch() {
        assertFalse(ConditionPredicates.test(CompareOperator.IN, "财务", "研发,测试,运维"));
    }

    @Test
    void in_numericValue_shouldMatchAcrossType() {
        // 场景：CSV 中数值是字符串 "8000"，IN 列表项 "8000"
        // 期望：Values.equals 跨类型相等 → true
        assertTrue(ConditionPredicates.test(CompareOperator.IN, 8000, "8000,9000"));
    }

    @Test
    void in_shouldTrimWhitespace() {
        assertTrue(ConditionPredicates.test(CompareOperator.IN, "研发", " 研发 , 测试 "));
    }

    @Test
    void in_emptyRight_shouldBeFalse() {
        assertFalse(ConditionPredicates.test(CompareOperator.IN, "研发", ""));
    }

    @Test
    void notIn_shouldBeInverse() {
        assertTrue(ConditionPredicates.test(CompareOperator.NOT_IN, "财务", "研发,测试"));
        assertFalse(ConditionPredicates.test(CompareOperator.NOT_IN, "研发", "研发,测试"));
    }

    // ─── IS_NULL / IS_NOT_NULL（null 或空串）───────────────────────

    @Test
    void isNull_nullValue_shouldBeTrue() {
        assertTrue(ConditionPredicates.test(CompareOperator.IS_NULL, null, null));
    }

    @Test
    void isNull_emptyString_shouldBeTrue() {
        assertTrue(ConditionPredicates.test(CompareOperator.IS_NULL, "", null));
    }

    @Test
    void isNull_nonEmpty_shouldBeFalse() {
        assertFalse(ConditionPredicates.test(CompareOperator.IS_NULL, "张三", null));
    }

    @Test
    void isNotNull_shouldBeInverse() {
        assertTrue(ConditionPredicates.test(CompareOperator.IS_NOT_NULL, "张三", null));
        assertFalse(ConditionPredicates.test(CompareOperator.IS_NOT_NULL, "", null));
        assertFalse(ConditionPredicates.test(CompareOperator.IS_NOT_NULL, null, null));
    }

    // ─── BETWEEN（闭区间 "low,high"，数值优先比较）───────────────────────

    @Test
    void between_inRange_shouldBeTrue() {
        assertTrue(ConditionPredicates.test(CompareOperator.BETWEEN, 50, "1,100"));
    }

    @Test
    void between_outOfRange_shouldBeFalse() {
        assertFalse(ConditionPredicates.test(CompareOperator.BETWEEN, 0, "1,100"));
        assertFalse(ConditionPredicates.test(CompareOperator.BETWEEN, 101, "1,100"));
    }

    @Test
    void between_boundaryInclusive() {
        // 闭区间：端点值也算命中
        assertTrue(ConditionPredicates.test(CompareOperator.BETWEEN, 1, "1,100"));
        assertTrue(ConditionPredicates.test(CompareOperator.BETWEEN, 100, "1,100"));
    }

    @Test
    void between_numericValueAcrossType() {
        // CSV 数值字符串 "8000" 与数值边界跨类型比较
        assertTrue(ConditionPredicates.test(CompareOperator.BETWEEN, "8000", "1000,9999"));
    }

    @Test
    void between_missingBound_shouldBeFalse() {
        // 缺少上界（只一个值）无法构成区间
        assertFalse(ConditionPredicates.test(CompareOperator.BETWEEN, 50, "1"));
    }
}
