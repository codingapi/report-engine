package com.example.report.model.source;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 数据集层：从某个连接里"选中的一张表 / 一条查询"，是报表真正绑定的粒度。
 *
 * <p>库里 50 张表，只用 3 张 → 只建 3 个 Dataset。这一层化解了"数据源整体 vs 单表使用"的两难：
 * 连接保持整体，报表使用粒度是单表。Dataset 永远是<b>单表/单查询</b>，不内嵌 JOIN，
 * 所有跨表关联交给 {@link Relationship}。
 *
 * <p>除物理表外，Dataset 也可是 <b>UNION 派生数据集</b>：{@link #union} 非空时，本数据集的行 =
 * 各成员数据集按映射对齐后<b>纵向追加</b>（同列拼行，成员间无需关系）。此时
 * {@link #datasourceId}/{@link #sourceTable} 忽略，{@link #fields} 即统一列。
 */
@Data
@Builder
public class Dataset {
    private String id;
    /** 来自哪个连接（UNION 派生数据集为 null） */
    private String datasourceId;
    /** 对应库里的表名（或一段 SQL；UNION 派生数据集为 null） */
    private String sourceTable;
    /** 显示名：学生 / 成绩 */
    private String alias;
    private List<Field> fields;
    /** 非空表示这是 UNION 派生数据集：成员行按映射对齐后纵向追加 */
    private List<UnionMember> union;
}
