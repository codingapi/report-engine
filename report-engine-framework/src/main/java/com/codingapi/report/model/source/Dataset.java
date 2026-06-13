package com.codingapi.report.model.source;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 数据集层：从某个连接里"选中的一张表 / 一条查询"，是报表真正绑定的粒度。
 *
 * <h3>为什么需要 Dataset 这一层？</h3>
 * <p>库里 50 张表，报表只用 3 张 → 只建 3 个 Dataset。这一层化解了"数据源整体 vs 单表使用"的两难：
 * <ul>
 *   <li><b>连接保持整体</b>：一个 DataSource 封装一个物理连接，不改</li>
 *   <li><b>报表使用粒度是单表</b>：每个 Dataset 是一张表，字段明确、可独立命名别名</li>
 *   <li><b>跨表关联交给 {@link Relationship}</b>：Dataset 永远不内嵌 JOIN，保持单表单查询的简单性</li>
 * </ul>
 *
 * <h3>两种 Dataset 形态</h3>
 * <p><b>1. 物理表 Dataset</b>（最常见）：{@link #union} 为空，直接对应一个连接下的一张表。
 * {@link #datasourceId} 指向连接，{@link #sourceTable} 是表名。
 *
 * <p><b>2. UNION 派生数据集</b>：{@link #union} 非空，用于"结构相同但分布在不同表/不同连接"的场景。
 * 典型例子：A 部门员工表和 B 部门员工表，列名不同但语义相同，需要合并为统一视图。
 * <pre>
 *   Dataset("all_depts", union=[
 *     UnionMember("dept_a", {name→name, gender→gender, age→age}),
 *     UnionMember("dept_b", {name→xm,    gender→xb,    age→nl})   // B 部门列名不同，在此对齐
 *   ])
 *   → 提取后：两个成员各自提取，行按统一列对齐后纵向追加
 * </pre>
 * UNION 派生数据集的 {@link #datasourceId}/{@link #sourceTable} 为 null（因为它不属于任何单个连接），
 * {@link #fields} 定义的是统一列（合并后的 schema）。
 *
 * <h3>为什么不直接用 SQL 视图？</h3>
 * <p>SQL 视图只能处理同一数据库内的合并。UNION 派生数据集支持<b>跨源</b>纵向合并
 * （如 DB 表 + CSV 文件），这在纯 SQL 方案里做不到。
 */
@Data
@Builder
public class Dataset {
    private String id;
    /** 来自哪个连接（UNION 派生数据集为 null，因为它的行来自多个连接） */
    private String datasourceId;
    /** 对应库里的表名（或一段 SQL 查询；UNION 派生数据集为 null） */
    private String sourceTable;
    /** 显示名（面向用户），如"员工表"、"成绩单"。用于属性面板下拉列表等 UI 展示 */
    private String alias;
    /**
     * 字段列表：描述该数据集有哪些列、每列的类型和是否主键。
     * <p>报表的 {@link com.codingapi.report.model.grid.FieldCell} 通过
     * {@link FieldRef} 引用这里的字段，而不是直接写字段名字符串。
     */
    private List<Field> fields;
    /**
     * UNION 成员列表：非空表示这是一个 UNION 派生数据集。
     * <p>每个成员指定一个源 Dataset 以及"统一列名 → 成员字段名"的映射，
     * 提取时各成员独立提取，行按映射对齐后纵向追加。
     * 成员间无需 Relationship（它们不是 JOIN，是 UNION ALL）。
     */
    private List<UnionMember> union;
}
