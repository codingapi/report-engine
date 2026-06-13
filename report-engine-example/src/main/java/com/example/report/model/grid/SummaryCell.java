package com.example.report.model.grid;

import com.example.report.model.source.FieldRef;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 小计/合计行里的一个单元格：要么是<b>标签文本</b>，要么是<b>对某字段的聚合</b>。
 *
 * <p>标签可含占位 <code>${group}</code>，渲染时替换为当前分组的值（如"研发中心小计"）。
 */
@Data
@AllArgsConstructor
public class SummaryCell {
    /** 落在哪一列 */
    private int column;
    /** 文本标签（与 field 二选一）；可含 ${group} */
    private String label;
    /** 聚合字段（与 label 二选一） */
    private FieldRef field;
    /** 聚合方式 */
    private Aggregation aggregation;

    /** 标签单元格 */
    public static SummaryCell label(int column, String label) {
        return new SummaryCell(column, label, null, null);
    }

    /** 聚合单元格 */
    public static SummaryCell agg(int column, FieldRef field, Aggregation aggregation) {
        return new SummaryCell(column, null, field, aggregation);
    }
}
