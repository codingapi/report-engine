package com.example.report.model.grid;

import lombok.Builder;
import lombok.Data;

/**
 * 文本格子：内容是一段文本，可含<b>参数占位</b>，<b>不引用任何数据字段、不扩展</b>。
 *
 * <h3>什么时候需要 TextCell？什么时候不需要？</h3>
 * <p>判断标准很简单：<b>文字里有没有动态参数</b>。
 * <ul>
 *   <li>"销售月报" → 纯静态文字，直接写在模板 Cell 的值里，<b>不需要</b> TextCell</li>
 *   <li>"${year}年度销售报表" → 有动态参数 {@code year}，<b>需要</b> TextCell</li>
 *   <li>"截至 ${asOfDate}" → 有动态参数 {@code asOfDate}，<b>需要</b> TextCell</li>
 * </ul>
 *
 * <h3>参数从哪来？</h3>
 * <p>占位符 {@code ${参数名}} 引用的是 {@link com.example.report.model.param.Parameter}。
 * 渲染时，引擎按参数名在 {@code ParamContext} 中查找：
 * <ol>
 *   <li>先查循环作用域（如果当前格子在 LoopBlock 内，可引用循环当前行的字段）</li>
 *   <li>再查报表级参数（{@code Report.parameters} 中定义的外部输入）</li>
 * </ol>
 *
 * <h3>与 FieldCell 的区别</h3>
 * <p>TextCell 只有文本模板，没有数据字段绑定、不扩展、不分组。
 * 它是 {@link CellBinding} 密封接口中较简单的一种，处理"文字里有动态片段"的场景。
 * 如果需要从数据集取数、扩展行/列、做聚合，应该用 {@link FieldCell}。
 */
@Data
@Builder
public final class TextCell implements CellBinding {

    /** 绑定到哪个格子（模板中的坐标） */
    private CellRef cell;

    /**
     * 文本模板，用 {@code ${参数名}} 表示动态片段。
     * <p>渲染时引擎用正则匹配 {@code $\{...\}} 占位符，替换为对应参数的值。
     * <p>示例：{@code "${year}年度销售报表"} → 渲染后 "2026年度销售报表"
     * <p>支持多个占位符混合：{@code "${year}年${month}月销售报表"} → "2026年6月销售报表"
     */
    private String template;
}
