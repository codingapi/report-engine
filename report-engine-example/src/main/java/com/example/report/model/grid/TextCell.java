package com.example.report.model.grid;

import lombok.Builder;
import lombok.Data;

/**
 * 文本格子：内容是一段文本，可含<b>参数占位</b>，<b>不引用任何数据字段、不扩展</b>。
 *
 * <p>典型场景：报表标题"${year}年度销售报表"——整体是纯文本，但 {@code year} 是运行时
 * 动态参数。渲染时把 {@code ${name}} 占位替换为对应报表参数的值。
 *
 * <p>注意区分：<b>没有任何参数</b>的纯静态标题文字，直接写在模板 Workbook 的 Cell 值里即可，
 * 无需登记 TextCell；只有"文字里嵌了动态参数"时才需要它。
 */
@Data
@Builder
public final class TextCell implements CellBinding {

    private CellRef cell;

    /**
     * 文本模板，用 {@code ${参数名}} 表示动态片段。
     * 例：{@code "${year}年度销售报表"}、{@code "截至 ${asOfDate}"}。
     */
    private String template;
}
