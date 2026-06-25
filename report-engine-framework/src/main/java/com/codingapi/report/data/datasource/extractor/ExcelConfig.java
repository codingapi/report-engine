package com.codingapi.report.data.datasource.extractor;

import com.codingapi.report.data.datasource.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Excel 数据源连接配置：从 {@link DataSource#getConfig()} 解析而来。
 *
 * <h3>配置项约定</h3>
 *
 * <ul>
 *   <li>{@code path}（必填）：.xlsx 文件路径
 *   <li>{@code sheetIndex}：工作表索引（0-based），默认 0
 *   <li>{@code headerRow}：表头所在行索引（0-based），默认 0
 * </ul>
 */
public record ExcelConfig(String path, int sheetIndex, int headerRow) {

    public static ExcelConfig from(DataSource source) {
        Map<String, Object> raw = source.getConfig() != null ? source.getConfig() : new HashMap<>();
        String path = str(raw.get("path"));
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("Excel 数据源缺少 config.path");
        }
        int sheetIndex = intOr(raw.get("sheetIndex"), 0);
        int headerRow = intOr(raw.get("headerRow"), 0);
        return new ExcelConfig(path, sheetIndex, headerRow);
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static int intOr(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
