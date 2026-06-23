package com.codingapi.report.operator.condition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 条件算子注册表测试：覆盖 CONTAINS/NOT_CONTAINS/IN/NOT_IN/IS_NULL/IS_NOT_NULL 六个算子，
 * 验证 {@link ConditionPredicates#test} 的策略分发与各算子语义（均在 Java 内存完成，不下推 SQL）。
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
}
