package com.codingapi.report.data.dataset;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * UNION 派生数据集：把多个数据集"结构相同但分布在不同表/不同连接"的数据，按字段映射纵向合并为统一视图。
 *
 * <h3>解决的问题</h3>
 * <p>典型例子：A 部门员工表和 B 部门员工表，列名不同但语义相同，需要合并为统一视图。
 * <pre>
 *   UnionDataset("all_depts", fields=[name, gender, age], members=[
 *     UnionMember("dept_a", {name→name, gender→gender, age→age}),
 *     UnionMember("dept_b", {name→xm,   gender→xb,    age→nl})   // B 部门列名不同，在此对齐
 *   ])
 *   → 提取后：两个成员各自提取，行按 fields 定义的统一列对齐后纵向追加
 * </pre>
 *
 * <h3>为什么没有 datasourceId / sourceTable？</h3>
 * <p>UNION 派生集的行来自多个成员数据集（可能跨多个连接），不属于任何单一连接，
 * 因此它没有物理表那套字段——这正是把它独立成型而非塞进 {@link TableDataset} 的原因。
 *
 * <h3>为什么不直接用 SQL 视图？</h3>
 * <p>SQL 视图只能处理同一数据库内的合并。UNION 派生集支持<b>跨源</b>纵向合并（如 DB 表 + CSV 文件），
 * 成员间无需 Relationship（它们不是 JOIN，是 UNION ALL）。
 */
@Data
@Builder
public final class UnionDataset implements Dataset {

    /** 数据集唯一标识。 */
    private String id;

    /** 显示名/别名（面向用户）。 */
    private String alias;

    /** 统一列定义（合并后的 schema）：各成员按 {@link UnionMember#mapping()} 对齐到这些列。 */
    private List<Field> fields;

    /**
     * UNION 成员列表：每个成员指定一个源数据集以及"统一列名 → 成员字段名"的映射。
     * <p>提取时各成员独立提取，行按映射对齐后纵向追加。
     */
    private List<UnionMember> members;
}
