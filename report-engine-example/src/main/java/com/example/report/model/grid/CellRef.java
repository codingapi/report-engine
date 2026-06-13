package com.example.report.model.grid;

/**
 * 单元格坐标（行列从 0 开始）。
 *
 * @param sheetId 所在 sheet
 * @param row     行号
 * @param column  列号
 */
public record CellRef(String sheetId, int row, int column) {
}
