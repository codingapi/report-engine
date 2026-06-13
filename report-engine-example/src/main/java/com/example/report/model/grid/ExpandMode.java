package com.example.report.model.grid;

/**
 * 扩展模式：决定扩展时是否对数据去重。仅当 {@link Expansion} 不为 NONE 时有意义。
 *
 * <h3>两种模式的区别</h3>
 * <p>以员工数据（3 人，2 个部门）为例：
 * <pre>
 *   原始数据：
 *   部门    姓名    工资
 *   研发    张三    8000
 *   研发    李四    9000
 *   测试    王五    7500
 *
 *   GROUP 模式（"部门"字段用 GROUP）：
 *   研发              ← 去重，只出现一次
 *   测试              ← 去重，只出现一次
 *
 *   LIST 模式（"姓名"字段用 LIST）：
 *   张三              ← 每条记录都出
 *   李四
 *   王五
 * </pre>
 *
 * <h3>典型组合</h3>
 * <p>多级分组表中，外层分组列用 GROUP，内层明细列用 LIST：
 * <pre>
 *   单位(GROUP) → 部门(GROUP) → 姓名(LIST) → 工资(LIST)
 * </pre>
 * GROUP 列通常配合 {@link FieldCell#isMergeRepeated()} 把相邻相同值合并为跨行单元格。
 */
public enum ExpandMode {
    /** 按值去重分组：相邻相同值只出一行/列，用于分组列表头 */
    GROUP,
    /** 明细全行：每条记录都出一行/列，不去重，用于最内层数据 */
    LIST
}
