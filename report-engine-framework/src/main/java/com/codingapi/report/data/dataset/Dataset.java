package com.codingapi.report.data.dataset;

import java.util.List;

/**
 * 数据集层（密封接口）：从数据源里"选中的一张表 / 一条查询"，是报表真正绑定的粒度。
 *
 * <h3>为什么是密封接口而不是带可空 union 字段的类？</h3>
 *
 * <p>数据集只有两种形态，且它们的字段构成<b>互斥</b>：物理表有连接和表名、没有 UNION 成员； UNION 派生集有成员映射、没有连接和表名。用"一个类 + 可空 {@code
 * union} 字段"来表达， 等于让一个对象同时携带两套互斥字段，靠"union 是否为空"当类型开关——这正是要消除的坏味道。
 * 拆成密封层级后，每种形态只携带自己该有的字段，引擎用模式匹配穷尽处理，新增形态编译器强制覆盖。 这与本项目的 {@code CellBinding}/{@code
 * ParamSource}/{@code Value} 是同一套建模范式。
 *
 * <h3>两种形态</h3>
 *
 * <ul>
 *   <li>{@link TableDataset}：物理表数据集，对应一个连接下的一张表（datasourceId + sourceTable）
 *   <li>{@link UnionDataset}：UNION 派生数据集，多个数据集按字段映射纵向合并为统一视图（members）
 * </ul>
 *
 * <h3>共同契约</h3>
 *
 * <p>无论哪种形态，都有稳定的 id、面向用户的别名、以及统一的字段列表（schema）—— 报表的 {@link
 * com.codingapi.report.expression.Value.FieldValue} 通过 {@link FieldRef}（datasetId + 字段名）引用这里的字段。
 *
 * @see TableDataset
 * @see UnionDataset
 */
public sealed interface Dataset permits TableDataset, UnionDataset {

    /** 数据集唯一标识，被 {@link FieldRef#datasetId()} 引用。 */
    String getId();

    /** 英文标识名（物理表名或合集名），与 {@link #getAlias()}（中文别名）成对。 */
    String getName();

    /** 显示名/别名（面向用户），如"员工表"、"全部人员"。用于属性面板等 UI 展示。 */
    String getAlias();

    /** 字段列表（统一 schema）：该数据集对外暴露的列定义，与具体形态无关。 */
    List<Field> getFields();
}
