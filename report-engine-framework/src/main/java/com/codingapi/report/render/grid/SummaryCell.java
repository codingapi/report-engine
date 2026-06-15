package com.codingapi.report.render.grid;

import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.operator.aggregation.Aggregation;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 小计/合计行里的一个单元格：要么是<b>标签文本</b>，要么是<b>对某字段的聚合</b>。
 *
 * <h3>两种角色（互斥）</h3>
 * <ul>
 *   <li><b>标签</b>（{@link #label} 非空）：显示文本，可含占位符 {@code ${group}}，
 *       渲染时替换为当前分组的值。例：{@code "${group}小计"} → "研发中心小计"</li>
 *   <li><b>聚合值</b>（{@link #field} 非空）：对某字段在当前分组范围内做聚合计算。
 *       例：{@code SUM(工资)} → 当前单位所有员工的工资总和</li>
 * </ul>
 *
 * <h3>示例组合</h3>
 * <pre>
 *   "总部小计"行 = [
 *     SummaryCell.label(0, ""),              // A 列：空
 *     SummaryCell.label(1, "${group}小计"),   // B 列：显示"总部小计"
 *     SummaryCell.label(2, ""),              // C 列：空
 *     SummaryCell.agg(3, "工资", SUM),        // D 列：SUM(工资) = 24500
 *   ]
 * </pre>
 *
 * <h3>聚合范围</h3>
 * <p>聚合值的计算范围取决于所属 {@link SummaryRow#getGroupBy()}：
 * <ul>
 *   <li>groupBy 非 null（小计）：仅聚合当前分组内的行</li>
 *   <li>groupBy 为 null（总计）：聚合全表所有行</li>
 * </ul>
 */
@Data
@AllArgsConstructor
public class SummaryCell {
    /** 落在哪一列（0-based），对应模板中的列坐标 */
    private int column;

    /**
     * 文本标签（与 {@link #field} 二选一）。
     * <p>可含占位符 {@code ${group}}，渲染时替换为当前分组的字段值。
     * <p>使用 {@link #label(int, String)} 工厂方法创建。
     */
    private String label;

    /**
     * 聚合字段（与 {@link #label} 二选一）。
     * <p>指定对哪个数据集的哪个字段做聚合计算。
     * <p>使用 {@link #agg(int, FieldRef, Aggregation)} 工厂方法创建。
     */
    private FieldRef field;

    /** 聚合方式（SUM/COUNT/AVG/MAX/MIN 等），仅当 field 非空时有意义 */
    private Aggregation aggregation;

    /**
     * 创建标签单元格。
     *
     * @param column 列坐标
     * @param label  文本标签，可含 {@code ${group}} 占位符
     */
    public static SummaryCell label(int column, String label) {
        return new SummaryCell(column, label, null, null);
    }

    /**
     * 创建聚合单元格。
     *
     * @param column      列坐标
     * @param field       聚合目标字段
     * @param aggregation 聚合方式
     */
    public static SummaryCell agg(int column, FieldRef field, Aggregation aggregation) {
        return new SummaryCell(column, null, field, aggregation);
    }
}
