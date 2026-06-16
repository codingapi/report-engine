package com.codingapi.report.render.grid;

import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.expression.Templates;
import com.codingapi.report.expression.Value;
import com.codingapi.report.operator.aggregation.Aggregation;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 小计/合计行里的一个单元格：落在某列上的一个<b>值表达式</b>。
 *
 * <h3>值统一为表达式</h3>
 * <p>标签和聚合不再是两个互斥字段，而是同一个 {@link #value}（{@link Value}）的不同形态：
 * <ul>
 *   <li><b>标签</b>：{@link Value.Template}，可含占位 {@code ${group}}（当前分组值，渲染期注入）。
 *       例：{@code "${group}小计"} → "研发中心小计"</li>
 *   <li><b>聚合</b>：{@link Value.Aggregate}，在当前分组行集合上汇总某字段。
 *       例：{@code SUM(工资)}</li>
 * </ul>
 * 工厂方法 {@link #label}/{@link #agg} 保持原有便捷写法不变，内部构造对应的 {@link Value} 节点。
 *
 * <h3>聚合范围</h3>
 * <p>取决于所属 {@link SummaryRow#getGroupBy()}：非 null（小计）只聚合当前分组内的行，null（总计）聚合全表。
 */
@Data
@AllArgsConstructor
public class SummaryCell {

    /** 落在哪一列（0-based）。 */
    private int column;

    /** 值表达式：标签（Template）或聚合（Aggregate）。 */
    private Value value;

    /**
     * 创建标签单元格。
     *
     * @param column 列坐标
     * @param label  文本标签，可含 {@code ${group}} 占位符（编译为含 NameRef 的 Template）
     */
    public static SummaryCell label(int column, String label) {
        return new SummaryCell(column, Templates.parse(label));
    }

    /**
     * 创建聚合单元格。
     *
     * @param column      列坐标
     * @param field       聚合目标字段
     * @param aggregation 聚合方式
     */
    public static SummaryCell agg(int column, FieldRef field, Aggregation aggregation) {
        return new SummaryCell(column, new Value.Aggregate(aggregation, new Value.FieldValue(field)));
    }
}
