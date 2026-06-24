package com.codingapi.report.data.dataset;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 物理表数据集：对应某个连接（{@code DataSource}）下的一张表 / 一条查询，最常见的数据集形态。
 *
 * <h3>定位</h3>
 *
 * <pre>
 *   DataSource（连接）→ TableDataset（表/查询）→ Report（报表）
 * </pre>
 *
 * 库里 50 张表，报表只用 3 张 → 只建 3 个 TableDataset。{@link #datasourceId} 指向连接， {@link #sourceTable} 是表名（或一段
 * SQL 查询）。跨表关联交给 {@link com.codingapi.report.data.relation.Relationship}，数据集本身永远是单表单查询。
 */
@Data
@Builder
public final class TableDataset implements Dataset {

    /** 数据集唯一标识。 */
    private String id;

    /** 来自哪个连接，指向 {@code DataSource.id}。 */
    private String datasourceId;

    /** 对应库里的表名（或一段 SQL 查询）。 */
    private String sourceTable;

    /** 显示名/别名（面向用户）。 */
    private String alias;

    /**
     * 字段列表：描述该表有哪些列、每列的类型和是否主键。
     *
     * <p>报表的 {@link com.codingapi.report.expression.Value.FieldValue} 通过 {@link FieldRef}
     * 引用这里的字段，而非直接写字段名字符串。
     */
    private List<Field> fields;
}
