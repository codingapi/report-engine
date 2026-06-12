package com.codingapi.report.excel.pojo;

import lombok.Data;

import java.util.List;

/**
 * Excel 工作簿模型，对应前端 ExcelWorkbook 快照结构。
 * <p>
 * 一个工作簿包含多个工作表（Sheet），是 Excel 文件的顶层容器。
 * 前端通过 extractSnapshot() 导出此结构，后端通过 ExcelExporter 将其转换为 .xlsx 文件；
 * 反之，ExcelImporter 可将 .xlsx 文件解析回此结构，供前端 renderSnapshot() 渲染。
 * </p>
 */
@Data
public class Workbook {

    /**
     * 工作表列表，每个 Sheet 对应 Excel 文件中的一个标签页。
     * 列表顺序即为标签页的显示顺序。
     */
    private List<Sheet> sheets;
}
