package com.codingapi.report.data.dataset;

import lombok.Builder;
import lombok.Data;

/**
 * 数据集中的一个字段（列）定义。
 *
 * <h3>在模型中的位置</h3>
 *
 * <pre>
 *   Dataset → List&lt;Field&gt;     ← 你在这里
 *   Value.FieldValue → FieldRef(datasetId, field) → 按 name 找到本对象
 * </pre>
 *
 * Field 是数据集的 schema 描述，报表通过 {@link FieldRef}（datasetId + 字段名）引用它， 渲染时再按 name 查到具体的 Field 对象获取
 * {@link #dataType} 等信息。
 *
 * <h3>为什么有 {@link #alias}？</h3>
 *
 * <p>物理字段名通常是英文/拼音（如 {@code xm}、{@code base_sal}），不适合直接展示给用户。 {@code alias}
 * 用于属性面板的字段选择列表、生成的报表表头等面向用户的场景。 <b>绑定引用只用 name，不用 alias</b>——改别名不破坏任何绑定。
 *
 * <h3>{@link #primaryKey} 的作用</h3>
 *
 * <p>标记主键字段，用于两个场景：
 *
 * <ul>
 *   <li>自动扫描 Relationship 时，外键指向的目标必须是主键
 *   <li>UI 层面可以在字段列表里标识主键列，辅助用户理解数据结构
 * </ul>
 */
@Data
@Builder
public class Field {
    /** 物理字段名（与数据库/文件中的列名一致），也是 FieldRef 引用的标识 */
    private String name;

    /** 显示名/别名（面向用户），可为 null 则回退到 name */
    private String alias;

    /** 业务层简化类型（STRING/NUMBER/DATE/DATETIME/BOOLEAN/JSON），决定可用算子和聚合方式 */
    private DataType dataType;

    /** 是否主键。影响 Relationship 自动扫描和 UI 标识 */
    private boolean primaryKey;
}
