package com.codingapi.report.data.dataset;

import com.codingapi.report.data.datasource.DataSource;
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
 * 库里 50 张表，报表只用 3 张 → 只建 3 个 TableDataset。{@link #datasource} 是它所属的连接（取数时直接用，
 * 无需再到 DataModel 里按 id 查找），{@link #sourceTable} 是表名（或一段 SQL 查询）。跨表关联交给 {@link
 * com.codingapi.report.data.relation.Relationship}，数据集本身永远是单表单查询。
 */
@Data
@Builder
public final class TableDataset implements Dataset {

    /** 数据集唯一标识。 */
    private String id;

    /**
     * 所属连接（聚合）：TableDataset 自带取数所需的 {@link DataSource}，由它自己负责"从哪取数"。
     *
     * <p>这样 {@code DataModel} 不再持有 datasources 列表——要拿连接直接 {@code dataset.getDatasource()}。
     */
    private DataSource datasource;

    /** 来自哪个连接，冗余存连接 id（= {@code datasource.getId()}），便于 DTO/展示。 */
    private String datasourceId;

    /**
     * 数据集标识名（面向引用）：物理表数据集即表名；SQL 数据集由用户自定义。 与 {@link #sourceTable} 解耦——后者是"怎么取数"（表名或 SQL），name 是"叫什么"。
     */
    private String name;

    /** 取数来源：物理表名，或一段 SELECT SQL（SQL 数据集）。 */
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

    /** 标识名：优先用显式 name；旧数据无 name 时回退 sourceTable（向后兼容）。 */
    @Override
    public String getName() {
        return name != null && !name.isBlank() ? name : sourceTable;
    }
}
