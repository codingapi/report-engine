package com.codingapi.report.render.grid;

import com.codingapi.report.data.dataset.FieldRef;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 小计/合计：在分组断点处插入的汇总带。按 {@link #axis} 转置——
 * 纵向汇总在数据带<b>下方追加一行</b>，横向汇总在数据带<b>右侧追加一列</b>。
 *
 * <h3>两种模式</h3>
 * <ul>
 *   <li>{@link #groupBy} 指向某个分组列的字段 → <b>小计</b>。
 *       每当该分组列的值切换时，在前一组结束后插入一条小计。
 *       例：groupBy="单位" → 每个单位的员工列完后，插一行"XX单位小计"</li>
 *   <li>{@link #groupBy} 为 null → <b>总计</b>。
 *       全表末尾插入一条，汇总全部数据</li>
 * </ul>
 *
 * <h3>渲染示例（纵向）</h3>
 * <pre>
 *   员工报表 + SummaryRow(groupBy=单位) + SummaryRow(groupBy=null)：
 *
 *   单位  部门  姓名  工资        ← 表头（CellBinding）
 *   总部  研发  张三  8000
 *   总部  研发  李四  9000
 *   总部  测试  王五  7500
 *   ─── 总部小计 ─── 24500 ───    ← SummaryRow(groupBy=单位)，"总部"切换为"分部"时插入
 *   分部  销售  赵六  6000
 *   ─── 分部小计 ───  6000 ───    ← SummaryRow(groupBy=单位)
 *   ─── 总计 ─────── 30500 ───    ← SummaryRow(groupBy=null)，全表末尾
 * </pre>
 *
 * <h3>位置的自适应</h3>
 * <p>小计/总计不需要预定义输出位置——渲染引擎在遍历数据时自动检测分组断点，
 * 在正确的位置插入，并把它后面的内容整体推开。数据量变化时位置自动适应。
 *
 * <h3>可组合性</h3>
 * <p>一个报表可以有多个 SummaryRow：
 * <ul>
 *   <li>多级小计：先按"单位"小计，再按"部门"小计</li>
 *   <li>小计 + 总计：分组小计 + 全表总计</li>
 * </ul>
 *
 * <h3>坐标抽象（主轴 / 交叉轴）</h3>
 * <p>字段名按 {@link Axis} 统一表达，不再硬编码行/列：{@link #mainPos} 是汇总声明的主轴位置、
 * {@link #crossFrom}/{@link #crossTo} 是它覆盖的交叉区间。纵向时主轴=行、交叉=列；横向时主轴=列、交叉=行。
 */
@Data
@Builder
public class SummaryRow {
    /**
     * 汇总方向。null 视为 {@link Axis#VERTICAL}（向后兼容旧配置）。
     * <p>纵向：在数据带下方追加一行；横向：在数据带右侧追加一列。
     */
    @Builder.Default
    private Axis axis = Axis.VERTICAL;

    /**
     * 触发分组的字段引用。
     * <p>非 null 时：每当该字段的值切换（即分组断点），插入一条小计。
     * <p>null 时：总计，全表末尾只插入一次。
     */
    private FieldRef groupBy;

    /**
     * 汇总作用的交叉区间起点（0-based，含）——纵向是列、横向是行。
     * <p>汇总通过 [crossFrom, crossTo] 显式声明它覆盖的交叉带——渲染时只对
     * 「交叉区间与本数据带有交集」的带生效（见 {@code ReportRenderer.summariesForBand}）。
     * 这让并列独立报表（同一批记录上多个无关系数据带）各自的汇总互不串扰：
     * 各带汇总区间落在各自的交叉段，谁也广播不到谁。
     */
    private int crossFrom;

    /** 汇总作用的交叉区间终点（0-based，含）——纵向是列、横向是行。见 {@link #crossFrom}。 */
    private int crossTo;

    /**
     * 该汇总的单元格列表（标签 + 聚合值）。
     * <p>每个 SummaryCell 要么是文本标签（如"研发中心小计"），
     * 要么是某字段的聚合值（如 SUM(工资)）。各 cell 的交叉坐标应落在 [crossFrom, crossTo] 内。
     */
    private List<SummaryCell> cells;

    /**
     * 设计态锚定的主轴位置（0-based）——汇总在模板表格中占据的实际行（纵向）或列（横向）。
     * <p>渲染时 framework 按 groupBy 动态追加到数据带末尾，不以此字段定位输出位置。
     * 此字段仅供模板 merge 跟随时识别"哪个模板 merge 属于哪条汇总"：
     * 当模板 merge 的起点等于某汇总的 mainPos 时，该 merge 应跟随汇总移动。
     * <p>null 时表示未指定（测试或无模板场景）。
     */
    private Integer mainPos;
}
