package com.codingapi.report.operator.aggregation;

/**
 * 聚合方式：对多条记录的值进行汇总计算。
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>{@link com.codingapi.report.expression.Value.Aggregate} 节点：
 *       格子级别的聚合，常用于 {@code expansion=NONE} 取单值（如"员工总数"），
 *       或配合 GROUP 模式在分组级别汇总</li>
 *   <li>{@link SummaryCell} 的 {@link SummaryCell#getValue()} 返回 {@code Value.Aggregate}：
 *       小计/总计行中的聚合计算（如"部门工资合计"）</li>
 * </ul>
 *
 * <h3>按数据类型的可用性</h3>
 * <p>前端属性面板会根据字段的 {@link com.codingapi.report.data.dataset.DataType} 智能过滤可用聚合方式：
 * <ul>
 *   <li>NUMBER：全部 7 种都可用（NONE/COUNT/COUNT_DISTINCT/SUM/AVG/MAX/MIN）</li>
 *   <li>STRING：NONE/COUNT/COUNT_DISTINCT/MAX/MIN（不可 SUM/AVG）</li>
 *   <li>DATE/DATETIME：NONE/COUNT/COUNT_DISTINCT/MAX/MIN（取最早/最晚日期）</li>
 *   <li>BOOLEAN：NONE/COUNT/COUNT_DISTINCT（COUNT_TRUE/COUNT_FALSE 是 TODO）</li>
 * </ul>
 */
public enum Aggregation {
    /** 不聚合：取原值（明细模式下每行各自的值） */
    NONE,
    /** 计数：统计记录条数 */
    COUNT,
    /** 去重计数：统计不重复的值个数（如"涉及几个部门"） */
    COUNT_DISTINCT,
    /** 求和：仅适用于 NUMBER 类型 */
    SUM,
    /** 平均值：仅适用于 NUMBER 类型 */
    AVG,
    /** 最大值：适用于 NUMBER/DATE/DATETIME/STRING */
    MAX,
    /** 最小值：适用于 NUMBER/DATE/DATETIME/STRING */
    MIN
}
