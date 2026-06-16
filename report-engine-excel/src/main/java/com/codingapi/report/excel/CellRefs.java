package com.codingapi.report.excel;

/**
 * Excel 单元格坐标工具：(row, col) ↔ A1 引用转换。
 * <p>列号/行号均 0-based，与 Workbook 模型一致；A1 引用列从 A 起、行从 1 起（如 (0,0)→"A1"）。
 */
public final class CellRefs {

    private CellRefs() {
    }

    /** (row, col) → A1 引用，如 (0,0)→"A1"、(2,27)→"AB3"。 */
    public static String toRef(int row, int col) {
        StringBuilder sb = new StringBuilder();
        int c = col;
        while (c >= 0) {
            sb.insert(0, (char) ('A' + c % 26));
            c = c / 26 - 1;
        }
        sb.append(row + 1);
        return sb.toString();
    }

    /** 列号 → 列字母，如 0→"A"、27→"AB"。 */
    public static String colToLetter(int col) {
        StringBuilder sb = new StringBuilder();
        int c = col;
        while (c >= 0) {
            sb.insert(0, (char) ('A' + c % 26));
            c = c / 26 - 1;
        }
        return sb.toString();
    }
}
