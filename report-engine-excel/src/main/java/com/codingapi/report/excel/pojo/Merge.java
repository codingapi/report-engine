package com.codingapi.report.excel.pojo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;

/**
 * 合并区域模型，对应前端 ExcelMerge 快照结构。
 * <p>
 * 定义一个矩形区域内的单元格合并，使用 startRow/startCol 定位左上角，
 * rowSpan/colSpan 定义合并范围。例如一个 2×3 的合并区域：
 * startRow=0, startCol=0, rowSpan=2, colSpan=3 表示合并 A1:C2。
 * </p>
 */
@Data
public class Merge {

    /** 起始行索引（0-based） */
    private int startRow;

    /** 起始列索引（0-based） */
    private int startCol;

    /** 合并的行数 */
    private int rowSpan;

    /** 合并的列数 */
    private int colSpan;

    /**
     * 合并区域的领域属性绑定列表。
     * Excel 构建时忽略，仅用于前端快照的 round-trip 保持。
     */
    private List<JsonNode> props;
}
