package com.codingapi.report.operator.condition;

/**
 * 条件比较算子：用于 {@link Condition} 中定义过滤条件。
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
 *   LIKE              ✓       ✗      ✗      ✗        ✗
 *   IN                ✓       ✓      ✓      ✓        ✗
 *   NOT_IN            ✓       ✓      ✓      ✓        ✗
 *   IS_NULL           ✓       ✓      ✓      ✓        ✓
 *   IS_NOT_NULL       ✓       ✓      ✓      ✓        ✓
 *   BETWEEN           ✗       ✓      ✓      ✓        ✗
 * </pre>
 * <p>STRING 不支持大小比较（GT/GE/LT/LE），因为没有自然的排序关系。
 * LIKE 仅用于字符串的模糊匹配。
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
    /** 模糊匹配：field LIKE pattern（仅 STRING 类型，如 "%张%"） */
    LIKE,
    /** 包含于列表：field IN (v1, v2, ...) */
    IN,
    /** 不包含于列表：field NOT IN (v1, v2, ...) */
    NOT_IN,
    /** 为空：field IS NULL */
    IS_NULL,
    /** 不为空：field IS NOT NULL */
    IS_NOT_NULL,
    /** 范围：field BETWEEN low AND high（仅数值/日期类型） */
    BETWEEN
}
