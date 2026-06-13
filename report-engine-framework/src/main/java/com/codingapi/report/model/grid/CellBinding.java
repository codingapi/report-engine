package com.codingapi.report.model.grid;

/**
 * 格子绑定（展现层覆盖）：附着在 Univer/Excel 模板某个格子上的<b>动态语义</b>。
 *
 * <h3>为什么要分"模板层"和"覆盖层"？</h3>
 * <p>这是本系统最关键的架构决策之一。报表的视觉呈现分为两部分：
 * <ul>
 *   <li><b>模板层</b> = Univer/Excel 工作簿（{@code report-engine-excel} 的
 *       {@code Workbook → Sheet → Cell}）。承载<b>全部静态内容</b>：纯标题文字、
 *       富文本、样式（字体/颜色/对齐）、边框、填充、合并、行列尺寸。
 *       用户在 Univer 里排版出什么样，模板就是什么样。</li>
 *   <li><b>覆盖层</b> = 本接口。只对"有动态行为"的格子按坐标附加语义。
 *       一个格子如果没有动态行为（如表头"员工姓名"），就只存在于模板层，
 *       <b>不需要</b>进入本模型。</li>
 * </ul>
 * 这种分离的好处：模板设计师可以专注于排版和美观，数据绑定可以专注于取数逻辑，
 * 两者互不干扰。渲染时 ReportRenderer 把两层合并——以模板为画布，把覆盖层语义"刷"上去。
 *
 * <h3>覆盖层的两种格子（密封接口，穷尽）</h3>
 * <ul>
 *   <li>{@link TextCell}：文本 + 参数占位（如 {@code "${year}年度报表"}）。
 *       虽然是文字，但包含动态参数，所以属于覆盖层。</li>
 *   <li>{@link FieldCell}：数据字段绑定——取数、扩展、分组、合并、父格链、聚合、条件过滤。
 *       报表的核心机制都在这里。</li>
 * </ul>
 *
 * <h3>序列化位置</h3>
 * <p>覆盖层最终序列化进模板的 {@code Cell.props}（JSON 字段）——
 * 这是 {@code report-engine-excel} 的 Cell 模型里预留的扩展槽位。
 * 循环块则进 {@code Sheet.loopBlocks}。Univer 本身不理解这些 props 的含义，
 * 只有 ReportRenderer 在渲染时读取并解释。
 *
 * @see TextCell
 * @see FieldCell
 */
public sealed interface CellBinding permits TextCell, FieldCell {

    /**
     * 绑定到哪个格子（对应模板 Workbook 中的 Cell 坐标）。
     * <p>返回的 {@link CellRef} 包含 sheetId + row + column，
     * 渲染时按此坐标找到模板中对应的 Cell，把动态语义"覆盖"上去。
     */
    CellRef getCell();
}
