package com.codingapi.report.operator.condition;

import com.codingapi.report.operator.Values;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 条件比较的类型转换测试。
 *
 * 核心策略：
 * - EQ/NE：统一转字符串比较（泛化能力强）
 * - GT/GE/LT/LE：数值优先（两端都能转 double 时按数值比）
 */
class ConditionTypeCoercionTest {

    // ─── EQ 测试（数值优先）──────────────────────

    @Test
    void eq_numberAndString_shouldMatch() {
        // 场景：左侧数值 1，右侧字符串 "1"
        // 期望：两端都能转 1.0 → 数值比较 → true
        assertTrue(Values.equals(1, "1"), "数值 1 应该匹配字符串 \"1\"");
    }

    @Test
    void eq_stringAndNumber_shouldMatch() {
        // 场景：左侧字符串 "1"，右侧数值 1
        // 期望：两端都能转 1.0 → 数值比较 → true
        assertTrue(Values.equals("1", 1), "字符串 \"1\" 应该匹配数值 1");
    }

    @Test
    void eq_csvNumericStrings_shouldMatch() {
        // 场景：CSV 数据中数值都是字符串，"8000" == "8000"
        // 期望：两端都能转 8000.0 → 数值比较 → true
        assertTrue(Values.equals("8000", "8000"), "字符串 \"8000\" 应该匹配 \"8000\"");
    }

    @Test
    void eq_strings_shouldMatch() {
        // 场景：两个字符串 "abc" == "abc"
        // 期望：不能转 double → 字符串比较 → true
        assertTrue(Values.equals("abc", "abc"), "字符串 \"abc\" 应该匹配 \"abc\"");
    }

    @Test
    void eq_differentStrings_shouldNotMatch() {
        // 场景：两个不同字符串 "abc" != "def"
        // 期望：不能转 double → 字符串比较 → false
        assertFalse(Values.equals("abc", "def"), "字符串 \"abc\" 不应该匹配 \"def\"");
    }

    @Test
    void eq_stringAndNumber_different() {
        // 场景：左侧字符串 "abc"，右侧数值 123
        // 期望：左端不能转 double → 字符串比较 "abc" != "123" → false
        assertFalse(Values.equals("abc", 123), "字符串 \"abc\" 不应该匹配数值 123");
    }

    @Test
    void eq_doubleAndInteger_shouldMatch() {
        // 场景：CSV NUMBER 字段解析为 Double 10.0，参数为 Integer 10
        // 期望：两端都能转 10.0 → 数值比较 → true
        // 这是 FullChainTest 的场景，必须正确处理
        assertTrue(Values.equals(10.0, 10), "Double 10.0 应该匹配 Integer 10");
    }

    @Test
    void eq_doubleFormats_shouldMatch() {
        // 场景：3.0 和 3
        // 期望：两端都能转 3.0 → 数值比较 → true
        assertTrue(Values.equals(3.0, 3), "Double 3.0 应该匹配 Integer 3");
    }

    // ─── GT/LT 测试（数值优先）──────────────────────

    @Test
    void gt_numbers_shouldCompareAsNumber() {
        // 场景：100 > 50
        // 期望：数值比较 → true
        assertTrue(Values.compare(100, 50) > 0, "100 应该大于 50");
    }

    @Test
    void gt_numericStrings_shouldCompareAsNumber() {
        // 场景："100" > "50"
        // 期望：两端都能转 double，数值比较 100 > 50 → true
        assertTrue(Values.compare("100", "50") > 0, "\"100\" 应该大于 \"50\"（数值比较）");
    }

    @Test
    void gt_mixedTypes_shouldCompareAsNumber() {
        // 场景：100 > "50"
        // 期望：两端都能转 double，数值比较 100 > 50 → true
        assertTrue(Values.compare(100, "50") > 0, "数值 100 应该大于字符串 \"50\"");
    }

    @Test
    void gt_nonNumericStrings_shouldCompareAsString() {
        // 场景："abc" > "abd"
        // 期望：不能转 double，字符串字典序比较 → false
        assertFalse(Values.compare("abc", "abd") > 0, "\"abc\" 按字典序应该小于 \"abd\"");
    }

    @Test
    void lt_numbers_shouldCompareAsNumber() {
        // 场景：50 < 100
        // 期望：数值比较 → true
        assertTrue(Values.compare(50, 100) < 0, "50 应该小于 100");
    }

    @Test
    void lt_numericStrings_shouldCompareAsNumber() {
        // 场景："50" < "100"
        // 期望：两端都能转 double，数值比较 50 < 100 → true
        // 注意：如果是字符串比较，"50" > "100"（字典序），但数值比较正确
        assertTrue(Values.compare("50", "100") < 0, "\"50\" 应该小于 \"100\"（数值比较）");
    }
}
