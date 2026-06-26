package com.codingapi.report.core.grid;

import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.expression.Templates;
import com.codingapi.report.expression.Value;
import lombok.Data;

/**
 * 小计/合计行里的一个单元格：落在某列上的一个<b>值表达式</b>。
 *
 * <h3>值统一为表达式</h3>
 *
 * <p>标签和聚合不再是两个互斥字段，而是同一个 {@link #value}（{@link Value}）的不同形态：
 *
 * <ul>
 *   <li><b>标签</b>：{@link Value.Template}，可含占位 {@code ${group}}（当前分组值，渲染期注入）。 例：{@code "${group}小计"}
 *       → "研发中心小计"
 *   <li><b>聚合</b>：{@link Value.Aggregate}，在当前分组行集合上汇总某字段。 例：{@code SUM(工资)}
 * </ul>
 *
 * 工厂方法 {@link #label}/{@link #agg} 保持原有便捷写法不变，内部构造对应的 {@link Value} 节点。
 *
 * <h3>聚合范围</h3>
 *
 * <p>取决于所属 {@link SummaryRow#getGroupBy()}：非 null（小计）只聚合当前分组内的行，null（总计）聚合全表。
 */
@Data
public class SummaryCell {

    /** 落在交叉轴的哪个位置（0-based）——纵向汇总是列号、横向汇总是行号。 */
    private int crossPos;

    /** 值表达式：标签（Template）或聚合（Aggregate）。 */
    private Value value;

    /**
     * 是否开启反查（drill-down）能力（默认 false）。
     *
     * <p>开启后，预览态下该汇总格渲染为可点击（蓝色链接样式），用户点击可查看聚合计算的明细数据。 仅对聚合格有意义；标签格开启无效果。
     *
     * <p>反查视图由 {@link #drillView} 指定；未指定时使用该格字段所属数据集作为默认视图。
     */
    private boolean drillEnabled;

    /**
     * 反查视图（数据集 id，可 null）。
     *
     * <p>指定反查时展示哪个数据集的明细数据。null 时回退到该格字段所属的数据集（每个数据集的默认视图=它本身）。 仅在 {@link #drillEnabled} = true
     * 时生效。
     */
    private String drillView;

    public SummaryCell(int crossPos, Value value) {
        this(crossPos, value, false, null);
    }

    public SummaryCell(int crossPos, Value value, boolean drillEnabled, String drillView) {
        this.crossPos = crossPos;
        this.value = value;
        this.drillEnabled = drillEnabled;
        this.drillView = drillView;
    }

    /**
     * 创建标签单元格。
     *
     * @param crossPos 交叉坐标（纵向=列号 / 横向=行号）
     * @param label 文本标签，可含 {@code ${group}} 占位符（编译为含 NameRef 的 Template）
     */
    public static SummaryCell label(int crossPos, String label) {
        return new SummaryCell(crossPos, Templates.parse(label));
    }

    /**
     * 创建聚合单元格。
     *
     * @param crossPos 交叉坐标（纵向=列号 / 横向=行号）
     * @param field 聚合目标字段
     * @param aggregation 聚合名（如 {@code "SUM"} / {@code "COUNT"}），由注册表分发
     */
    public static SummaryCell agg(int crossPos, FieldRef field, String aggregation) {
        return new SummaryCell(
                crossPos, new Value.Aggregate(aggregation, new Value.FieldValue(field)));
    }
}
