package com.codingapi.report.operator.condition;

/**
 * 条件比较算子：用于 {@link Condition} 中定义过滤条件。
 *
 * <p>所有比较都在 Java 内存完成（不下推 SQL），因此算子语义均为 Java 原生字符串/数值/集合判定，
 * 不依赖任何 SQL 方言（无 LIKE 通配符、无 BETWEEN 语法）。
 *
 * <h3>按数据类型的可用性</h3>
 * <p>前端属性面板根据字段的 {@link com.codingapi.report.data.dataset.DataType} 智能过滤可用算子：
 * <pre>
 *                    STRING  NUMBER  DATE  DATETIME  BOOLEAN
 *   EQ (=)            ✓       ✓      ✓      ✓        ✓
 *   NE (≠)            ✓       ✓      ✓      ✓        ✓
 *   GT (>)            ✗       ✓      ✓      ✓        ✗
 *   GE (≥)            ✗       ✓      ✓      ✓        ✗
 *   LT (<)            ✗       ✓      ✓      ✓        ✗
 *   LE (≤)            ✗       ✓      ✓      ✓        ✗
 *   CONTAINS          ✓       ✗      ✗      ✗        ✗
 *   NOT_CONTAINS      ✓       ✗      ✗      ✗        ✗
 *   IN                ✓       ✓      ✓      ✓        ✗
 *   NOT_IN            ✓       ✓      ✓      ✓        ✗
 *   IS_NULL           ✓       ✓      ✓      ✓        ✓
 *   IS_NOT_NULL       ✓       ✓      ✓      ✓        ✓
 * </pre>
 * <p>STRING 不支持大小比较（GT/GE/LT/LE），因为没有自然的排序关系。
 * CONTAINS 为子串包含（Java {@code String.contains}），仅用于 STRING 类型。
 */
public enum CompareOperator {
    /** 等于：field = value */
    EQ,
    /** 不等于：field ≠ value */
    NE,
    /** 大于：field > value（仅数值/日期类型） */
    GT,
    /** 大于等于：field ≥ value */
    GE,
    /** 小于：field < value */
    LT,
    /** 小于等于：field ≤ value */
    LE,
    /** 包含：field 包含 value 子串（仅 STRING 类型，Java子串匹配） */
    CONTAINS,
    /** 不包含：field 不包含 value 子串 */
    NOT_CONTAINS,
    /** 包含于列表：field IN (v1, v2, ...)，右值为逗号分隔字符串 */
    IN,
    /** 不包含于列表：field NOT IN (v1, v2, ...) */
    NOT_IN,
    /** 为空：field IS NULL（null 或空串） */
    IS_NULL,
    /** 不为空：field IS NOT NULL */
    IS_NOT_NULL
}
