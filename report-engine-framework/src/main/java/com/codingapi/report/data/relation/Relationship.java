package com.codingapi.report.data.relation;

import com.codingapi.report.data.dataset.FieldRef;
import lombok.Builder;
import lombok.Data;

/**
 * 关系层：跨数据集的关联（JOIN 规格）。
 *
 * <h3>为什么挂在 DataModel/Report 上，而不是挂在 DataSource 上？</h3>
 *
 * <p>关系本质上是"两个 Dataset 之间"的事，而两个 Dataset 可能来自不同的 DataSource （如人事库的员工表 JOIN 薪资库的薪资表）。如果关系挂在某个
 * DataSource 上， 跨源关系就无处安放。因此关系独立存在：
 *
 * <ul>
 *   <li>通用关系 → 挂在 {@link DataModel#getRelationships()}，所有引用该模型的报表共享
 *   <li>报表专有关系 → 挂在 {@link com.codingapi.report.core.Report#getExtraRelationships()}， 仅本报表可见
 * </ul>
 *
 * <h3>它不是 SQL JOIN</h3>
 *
 * <p>本系统的计算全在 Java 内存完成，Relationship 是喂给 {@code Operators.join()} 的规格：
 *
 * <pre>
 *   Relationship(employees.id ←→ salaries.emp_id, INNER)
 *   → Operators.join(empRawTable, salRawTable, relationship)
 *   → 内存中做 hash join，产出合并后的 RawTable
 * </pre>
 *
 * 正因为不依赖 SQL，才能支持<b>跨源 join</b>（MySQL 表 JOIN CSV 文件）。
 *
 * <h3>关系从哪来？</h3>
 *
 * <p>由 {@link #origin} 区分：
 *
 * <ul>
 *   <li>{@code AUTO}：从数据库外键元数据自动扫描，适合单库内的标准关联
 *   <li>{@code MANUAL}：用户在界面上手动拖线连接，支持跨源、跨库的任意关联
 * </ul>
 */
@Data
@Builder
public class Relationship {
    private String id;

    /** 关联左端：某数据集的某字段（如 employees.id） */
    private FieldRef left;

    /** 关联右端：某数据集的某字段（如 salaries.emp_id） */
    private FieldRef right;

    /** JOIN 类型（INNER/LEFT/RIGHT/FULL），当前引擎只实现了 INNER */
    private JoinType joinType;

    /** 关系来源：自动扫描（外键）还是用户手动连线 */
    private RelationOrigin origin;
}
