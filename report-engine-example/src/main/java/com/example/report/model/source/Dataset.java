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
 */
@Data
@Builder
public class Dataset {
    private String id;
    /** 来自哪个连接 */
    private String datasourceId;
    /** 对应库里的表名（或一段 SQL） */
    private String sourceTable;
    /** 显示名：学生 / 成绩 */
    private String alias;
    private List<Field> fields;
}
