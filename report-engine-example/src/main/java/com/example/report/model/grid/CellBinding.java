package com.example.report.model.grid;

/**
 * 格子绑定（展现层覆盖）：附着在 Univer/Excel 模板某个格子上的<b>动态语义</b>。
 *
 * <p><b>分层很重要——区分"模板层"和"覆盖层"：</b>
 * <ul>
 *   <li><b>模板层</b> = Univer/Excel 工作簿（{@code report-engine-excel} 的
 *       {@code Workbook → Sheet → Cell}）。它承载<b>全部静态文本、富文本、样式、边框、
 *       填充、合并、行列尺寸</b>。纯标题文字（如"销售月报"）就是普通 Cell 的值 + 样式，
 *       <b>不需要进入本模型</b>。</li>
 *   <li><b>覆盖层</b> = 本接口。只对"有动态行为"的格子按坐标附加语义，最终序列化进
 *       模板的 {@code Cell.props}（循环块进 {@code Sheet.loopBlocks}）——这两个槽位
 *       Univer 结构里已经预留。</li>
 * </ul>
 *
 * <p>覆盖层的格子有两种（密封）：
 * <ul>
 *   <li>{@link TextCell}：文本，可含参数占位（如"${year}年度报表"）。纯文字但带动态参数。</li>
 *   <li>{@link FieldCell}：数据字段绑定（取数 + 行/列扩展 + 分组合并 + 父格 + 条件）。</li>
 * </ul>
 */
public sealed interface CellBinding permits TextCell, FieldCell {

    /** 绑定到哪个格子（对应模板 Workbook 中的 Cell 坐标） */
    CellRef getCell();
}
