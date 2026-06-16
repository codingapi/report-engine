package com.example.report.config;

import com.codingapi.report.excel.CellRefs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 示例报表配置的链式构造器，收敛 {@link ReportTemplateSeeder} 的 Map 样板。
 * <p>
 * 产物是与后端 JSON 契约一致的 {@code Map<String,Object>}（name/dataModelId/cellBindings/
 * loopBlocks/summaries/params/template）。值工厂为静态方法，便于在 seed 方法内直接组装。
 */
public class ReportConfigBuilder {

    private static final String SHEET = "sheet1";

    private final Map<String, Object> config;
    private final List<Map<String, Object>> bindings = new ArrayList<>();
    private final List<Map<String, Object>> summaries = new ArrayList<>();
    private final List<Map<String, Object>> loopBlocks = new ArrayList<>();
    private List<CellData> templateCells = List.of();
    private int colCount;
    private int rowCount;

    public ReportConfigBuilder(String name) {
        this.config = new LinkedHashMap<>();
        config.put("name", name);
        config.put("dataModelId", "default");
        config.put("_example", true);
        config.put("cellBindings", List.of());
        config.put("loopBlocks", List.of());
        config.put("summaries", List.of());
        config.put("params", List.of());
    }

    // ─── 链式：单元格绑定 ───

    public ReportConfigBuilder binding(int row, int col, Map<String, Object> value, String expansion, String expandMode) {
        return binding(row, col, value, expansion, expandMode, false, null);
    }

    public ReportConfigBuilder binding(int row, int col, Map<String, Object> value, String expansion, String expandMode,
                                       boolean mergeRepeated) {
        return binding(row, col, value, expansion, expandMode, mergeRepeated, null);
    }

    public ReportConfigBuilder binding(int row, int col, Map<String, Object> value, String expansion, String expandMode,
                                       boolean mergeRepeated, String parentCell) {
        bindings.add(makeBinding(row, col, value, expansion, expandMode, mergeRepeated, parentCell, List.of()));
        return this;
    }

    public ReportConfigBuilder bindingWithConditions(int row, int col, Map<String, Object> value,
                                                     String expansion, String expandMode, Map<String, Object> condition) {
        // Deep-copy condition with unique id
        Map<String, Object> c = new LinkedHashMap<>(condition);
        c.put("id", "c_" + row + "_" + col);
        bindings.add(makeBinding(row, col, value, expansion, expandMode, false, null, List.of(c)));
        return this;
    }

    private Map<String, Object> makeBinding(int row, int col, Map<String, Object> value, String expansion, String expandMode,
                                            boolean mergeRepeated, String parentCell, List<Map<String, Object>> conditions) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("cellKey", cellKey(row, col));
        b.put("value", value);
        b.put("expansion", expansion);
        b.put("expandMode", expandMode);
        b.put("mergeRepeated", mergeRepeated);
        b.put("parentCell", parentCell);
        b.put("conditions", conditions);
        return b;
    }

    // ─── 链式：汇总行 ───

    public ReportConfigBuilder summary(int row, int fromColumn, int toColumn,
                                       Map<String, Object> groupBy, List<Map<String, Object>> cells) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", "sum-" + System.nanoTime());
        s.put("row", row);
        s.put("fromColumn", fromColumn);
        s.put("toColumn", toColumn);
        s.put("groupBy", groupBy);
        s.put("cells", cells);
        summaries.add(s);
        return this;
    }

    // ─── 链式：循环块（原始 Map，示例特有结构） ───

    public ReportConfigBuilder loopBlock(Map<String, Object> block) {
        loopBlocks.add(block);
        return this;
    }

    // ─── 链式：模板表格 ───

    public ReportConfigBuilder template(int colCount, int rowCount, CellData... cells) {
        this.colCount = colCount;
        this.rowCount = rowCount;
        this.templateCells = List.of(cells);
        return this;
    }

    // ─── 构建 ───

    public Map<String, Object> build() {
        config.put("cellBindings", List.copyOf(bindings));
        if (!summaries.isEmpty()) {
            config.put("summaries", List.copyOf(summaries));
        }
        if (!loopBlocks.isEmpty()) {
            config.put("loopBlocks", List.copyOf(loopBlocks));
        }
        config.put("template", buildWorkbook(templateCells, colCount, rowCount));
        return config;
    }

    // ============================================================
    // 静态值工厂
    // ============================================================

    public static String cellKey(int row, int col) {
        return SHEET + ":" + row + ":" + col;
    }

    public static Map<String, Object> literal(String text) {
        return Map.of("type", "Literal", "payload", text);
    }

    public static Map<String, Object> fieldValue(String datasetId, String field) {
        return Map.of("type", "FieldValue", "payload", datasetId + "." + field);
    }

    public static Map<String, Object> aggregate(String agg, String datasetId, String field) {
        return Map.of("type", "Aggregate", "aggregation", agg, "operand", fieldValue(datasetId, field));
    }

    public static Map<String, Object> labelCell(int column, String label) {
        Map<String, Object> value;
        if (label.contains("${")) {
            // 构造 Template：解析 ${...} 占位为 NameRef
            value = buildTemplateValue(label);
        } else {
            value = Map.of("type", "Literal", "payload", label);
        }
        return Map.of("column", column, "value", value);
    }

    public static Map<String, Object> aggCell(int column, String payload, String aggregation) {
        return Map.of("column", column, "value", Map.of(
                "type", "Aggregate",
                "aggregation", aggregation,
                "operand", Map.of("type", "FieldValue", "payload", payload)
        ));
    }

    /** 构造 Template Value：支持 ${name} 占位符（编译为 NameRef） */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildTemplateValue(String text) {
        List<Map<String, Object>> parts = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int start = text.indexOf("${", i);
            if (start == -1) {
                parts.add(Map.of("kind", "text", "text", text.substring(i)));
                break;
            }
            if (start > i) {
                parts.add(Map.of("kind", "text", "text", text.substring(i, start)));
            }
            int end = text.indexOf("}", start + 2);
            String name = text.substring(start + 2, end);
            parts.add(Map.of("kind", "hole", "value", Map.of("type", "NameRef", "payload", name)));
            i = end + 1;
        }
        // 如果整个字符串就是一个洞，直接返回洞内 Value
        if (parts.size() == 1 && "hole".equals(parts.get(0).get("kind"))) {
            return (Map<String, Object>) parts.get(0).get("value");
        }
        return Map.of("type", "Template", "parts", parts);
    }

    public static CellData cell(int row, int col, String value) {
        return new CellData(row, col, value);
    }

    public record CellData(int row, int col, String value) {
    }

    // ─── ExcelWorkbook 构建 ───

    private static Map<String, Object> buildWorkbook(List<CellData> cells, int colCount, int rowCount) {
        List<Map<String, Object>> excelCells = new ArrayList<>();
        for (CellData c : cells) {
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("row", c.row());
            cell.put("col", c.col());
            cell.put("ref", CellRefs.toRef(c.row(), c.col()));
            cell.put("value", c.value());
            excelCells.add(cell);
        }

        Map<String, Object> sheet = new LinkedHashMap<>();
        sheet.put("id", SHEET);
        sheet.put("name", "Sheet1");
        sheet.put("rowCount", rowCount);
        sheet.put("columnCount", colCount);
        sheet.put("defaultRowHeight", 25);
        sheet.put("defaultColumnWidth", 100);
        sheet.put("merges", List.of());
        sheet.put("cells", excelCells);
        sheet.put("rows", List.of());
        sheet.put("columns", List.of());

        return Map.of("sheets", List.of(sheet));
    }
}
