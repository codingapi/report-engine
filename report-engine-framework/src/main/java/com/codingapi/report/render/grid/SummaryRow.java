package com.codingapi.report.render.grid;

import com.codingapi.report.data.dataset.FieldRef;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 小计/合计行：在分组断点处插入的汇总行。
 *
 * <h3>两种模式</h3>
 * <ul>
 *   <li>{@link #groupBy} 指向某个分组列的字段 → <b>小计</b>。
 *       每当该分组列的值切换时，在前一组结束后插入一行小计。
 *       例：groupBy="单位" → 每个单位的员工列完后，插一行"XX单位小计"</li>
 *   <li>{@link #groupBy} 为 null → <b>总计</b>。
 *       全表末尾插入一行，汇总全部数据</li>
 * </ul>
 *
 * <h3>渲染示例</h3>
 * <pre>
 *   员工报表 + SummaryRow(groupBy=单位) + SummaryRow(groupBy=null)：
 *
 *   单位  部门  姓名  工资        ← 表头（FieldCell）
 *   总部  研发  张三  8000
 *   总部  研发  李四  9000
 *   总部  测试  王五  7500
 *   ─── 总部小计 ─── 24500 ───    ← SummaryRow(groupBy=单位)，"总部"切换为"分部"时插入
 *   分部  销售  赵六  6000
 *   ─── 分部小计 ───  6000 ───    ← SummaryRow(groupBy=单位)
 *   ─── 总计 ─────── 30500 ───    ← SummaryRow(groupBy=null)，全表末尾
 * </pre>
 *
 * <h3>行位置的自适应</h3>
 * <p>小计/总计行不需要预定义行号——渲染引擎在遍历数据时自动检测分组断点，
 * 在正确的位置插入行，并把它后面的内容整体下推。数据量变化时位置自动适应。
 *
 * <h3>可组合性</h3>
 * <p>一个报表可以有多个 SummaryRow：
 * <ul>
 *   <li>多级小计：先按"单位"小计，再按"部门"小计</li>
 *   <li>小计 + 总计：分组小计 + 全表总计</li>
 * </ul>
 */
@Data
@Builder
public class SummaryRow {
    /**
     * 触发分组的字段引用。
     * <p>非 null 时：每当该字段的值切换（即分组断点），插入一行小计。
     * <p>null 时：总计，全表末尾只插入一次。
     */
    private FieldRef groupBy;

    /**
     * 该汇总行的单元格列表（标签 + 聚合值）。
     * <p>每个 SummaryCell 要么是文本标签（如"研发中心小计"），
     * 要么是某字段的聚合值（如 SUM(工资)）。
     */
    private List<SummaryCell> cells;
}
