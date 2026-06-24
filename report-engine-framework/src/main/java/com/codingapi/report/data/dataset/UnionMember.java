package com.codingapi.report.data.dataset;

import java.util.Map;

/**
 * UNION 成员：把一个数据集的字段映射到 UNION 派生数据集的统一列。
 *
 * <h3>解决的问题</h3>
 *
 * <p>现实中"结构相同但列名不同"的多张表很常见：
 *
 * <ul>
 *   <li>A 部门员工表列名：{@code name, gender, age}
 *   <li>B 部门员工表列名：{@code xm, xb, nl}（用拼音缩写）
 * </ul>
 *
 * 直接 UNION 会因为列名不匹配而失败。UnionMember 通过显式的字段映射解决对齐问题。
 *
 * <h3>工作原理</h3>
 *
 * <pre>
 *   Dataset("all_depts") 的 fields = [name, gender, age]    ← 统一列
 *   ├── UnionMember("dept_a", {name→name, gender→gender, age→age})
 *   └── UnionMember("dept_b", {name→xm,   gender→xb,    age→nl})
 *
 *   提取过程：
 *   1. 提取 dept_a → RawTable(name, gender, age)
 *   2. 提取 dept_b → RawTable(xm, xb, nl)
 *   3. 按 mapping 重命名列 → RawTable(name, gender, age)
 *   4. 纵向追加上一步结果 → 合并后的 RawTable
 * </pre>
 *
 * <p>与 SQL UNION 的区别：这里的成员可以来自不同连接类型（如 DB 表 + CSV 文件）， SQL UNION 只能处理同一数据库内的表。
 *
 * @param datasetId 成员数据集 id，指向 {@link Dataset#getId()}
 * @param mapping 统一列名 → 成员字段名。key 是 UNION 数据集的列名（即 {@link Dataset#getFields()} 中的 name），value
 *     是该成员数据集的实际字段名
 */
public record UnionMember(String datasetId, Map<String, String> mapping) {}
