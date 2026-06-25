package com.codingapi.report.datasource.excel;

import com.codingapi.report.data.dataset.DataType;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.datasource.DataExtractor;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.DataSourceType;
import com.codingapi.report.data.datasource.RawTable;
import com.codingapi.report.data.datasource.TestResult;
import com.codingapi.report.excel.ExcelImporter;
import com.codingapi.report.excel.pojo.Cell;
import com.codingapi.report.excel.pojo.Sheet;
import com.codingapi.report.excel.pojo.Workbook;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Excel 文件数据提取器：用 {@link ExcelImporter} 解析 .xlsx，首行当表头、后续行当数据， 按 {@link
 * Dataset#getFields()} 映射为 {@link RawTable}。
 *
 * <h3>设计要点</h3>
 *
 * <ul>
 *   <li>复用 {@code report-engine-excel} 的 {@link ExcelImporter}（POI 封装），不重复造轮子
 *   <li>{@code Workbook → Sheet → Cell} POJO 模型，{@link Cell#getValue()} 是 Jackson {@link JsonNode}
 *       （保留原始类型：字符串/数字/布尔/空值）
 *   <li>列名用限定名 {@code datasetId.field}（与 {@code DbDataExtractor} / {@code ApiDataExtractor} 一致）
 *   <li>类型归一：{@code NUMBER→Double}、{@code BOOLEAN→Boolean}、其余 → {@code String}
 *   <li>{@code config}：{@code path}（必填）、{@code sheetIndex}（默认 0）、{@code headerRow}（默认 0）
 *   <li>{@link #test(DataSource)} 校验文件可读且能被 ExcelImporter 解析
 * </ul>
 */
@Slf4j
public class ExcelDataExtractor implements DataExtractor {

    private final ExcelImporter excelImporter = new ExcelImporter();

    @Override
    public boolean supports(DataSourceType type) {
        return type == DataSourceType.EXCEL;
    }

    @Override
    public RawTable extract(DataSource source, Dataset dataset) {
        ExcelConfig config = ExcelConfig.from(source);
        log.debug("Excel 提取: {} path={} sheetIndex={}", dataset.getId(), config.path(), config.sheetIndex());
        try (InputStream input = openStream(config.path())) {
            Workbook workbook = excelImporter.importFrom(input);
            Sheet sheet = pickSheet(workbook, config.sheetIndex());
            return readRows(sheet, dataset, config.headerRow());
        } catch (IOException e) {
            throw new IllegalStateException("Excel 提取失败: " + dataset.getId(), e);
        }
    }

    @Override
    public TestResult test(DataSource source) {
        long start = System.currentTimeMillis();
        try {
            ExcelConfig config = ExcelConfig.from(source);
            try (InputStream input = openStream(config.path())) {
                excelImporter.importFrom(input);
            }
            long latency = System.currentTimeMillis() - start;
            return new TestResult(true, "文件读取成功", latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new TestResult(false, "文件读取失败: " + e.getMessage(), latency);
        }
    }

    // ============================================================
    // 内部
    // ============================================================

    private RawTable readRows(Sheet sheet, Dataset dataset, int headerRow) {
        List<Cell> cells = sheet.getCells() != null ? sheet.getCells() : new ArrayList<>();

        // 按行分组：row → (col → Cell)
        Map<Integer, Map<Integer, Cell>> byRow = new LinkedHashMap<>();
        for (Cell c : cells) {
            byRow.computeIfAbsent(c.getRow(), k -> new HashMap<>()).put(c.getCol(), c);
        }

        // 表头：headerRow 的 col → 列名
        Map<Integer, String> headerByCol = new HashMap<>();
        Map<Integer, Cell> headerRowCells = byRow.getOrDefault(headerRow, new HashMap<>());
        for (Map.Entry<Integer, Cell> e : headerRowCells.entrySet()) {
            String name = textOf(e.getValue().getValue());
            if (name != null && !name.isBlank()) {
                headerByCol.put(e.getKey(), name.trim());
            }
        }

        // 字段名 → Field（按名匹配表头列）
        Map<String, Field> fieldByName = new HashMap<>();
        for (Field f : dataset.getFields()) {
            fieldByName.put(f.getName(), f);
        }

        // 限定列名顺序
        List<String> columns = new ArrayList<>();
        for (Field f : dataset.getFields()) {
            columns.add(dataset.getId() + "." + f.getName());
        }

        // 数据行：row > headerRow
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Integer, Map<Integer, Cell>> rowEntry : byRow.entrySet()) {
            int rowIdx = rowEntry.getKey();
            if (rowIdx <= headerRow) continue;
            Map<Integer, Cell> rowCells = rowEntry.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            // 初始化所有字段为 null
            for (Field f : dataset.getFields()) {
                row.put(dataset.getId() + "." + f.getName(), null);
            }
            for (Map.Entry<Integer, Cell> e : rowCells.entrySet()) {
                String colName = headerByCol.get(e.getKey());
                if (colName == null) continue;
                Field f = fieldByName.get(colName);
                if (f == null) continue;
                row.put(dataset.getId() + "." + f.getName(), coerce(e.getValue().getValue(), f.getDataType()));
            }
            rows.add(row);
        }
        return new RawTable(columns, rows);
    }

    private Sheet pickSheet(Workbook workbook, int sheetIndex) {
        List<Sheet> sheets = workbook.getSheets();
        if (sheets == null || sheets.isEmpty()) {
            throw new IllegalStateException("Excel 工作簿无工作表");
        }
        if (sheetIndex < 0 || sheetIndex >= sheets.size()) {
            throw new IllegalStateException("sheetIndex 越界: " + sheetIndex + " / " + sheets.size());
        }
        return sheets.get(sheetIndex);
    }

    private InputStream openStream(String path) throws IOException {
        Path p = Paths.get(path);
        if (!Files.exists(p)) {
            throw new IOException("文件不存在: " + path);
        }
        return new FileInputStream(p.toFile());
    }

    private static Object coerce(JsonNode value, DataType type) {
        if (value == null || value.isNull()) return null;
        return switch (type) {
            case NUMBER -> value.isNumber() ? value.doubleValue() : Double.parseDouble(value.asText());
            case BOOLEAN -> value.isBoolean() ? value.booleanValue() : Boolean.parseBoolean(value.asText());
            default -> value.isTextual() ? value.asText() : value.toString();
        };
    }

    private static String textOf(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (value.isTextual()) return value.asText();
        return value.asText();
    }
}
