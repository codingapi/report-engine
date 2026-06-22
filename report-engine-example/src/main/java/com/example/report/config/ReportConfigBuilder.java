package com.example.report.config;

import com.codingapi.report.config.ReportConfig;
import com.codingapi.report.config.dto.ConfigDtos.BindingDTO;
import com.codingapi.report.config.dto.ConfigDtos.ConditionDTO;
import com.codingapi.report.config.dto.ConfigDtos.FieldRefDTO;
import com.codingapi.report.config.dto.ConfigDtos.LoopBlockDTO;
import com.codingapi.report.config.dto.ConfigDtos.PartDTO;
import com.codingapi.report.config.dto.ConfigDtos.SourceDTO;
import com.codingapi.report.config.dto.ConfigDtos.SummaryCellDTO;
import com.codingapi.report.config.dto.ConfigDtos.SummaryRowDTO;
import com.codingapi.report.config.dto.ConfigDtos.ValueDTO;
import com.codingapi.report.excel.CellRefs;
import com.codingapi.report.excel.pojo.Cell;
import com.codingapi.report.excel.pojo.Sheet;
import com.codingapi.report.excel.pojo.Workbook;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 示例报表配置的链式构造器，收敛 {@link ReportTemplateSeeder} 的样板。
 * <p>
 * 产物是强类型 {@link ReportConfig}（name/dataModelId/cellBindings/loopBlocks/summaries/params/template）。
 * 值工厂为静态方法（返回 {@link ValueDTO}），便于在 seed 方法内直接组装。
 */
public class ReportConfigBuilder {

    private static final String SHEET = "sheet1";

    private final String name;
    private final List<BindingDTO> bindings = new ArrayList<>();
    private final List<SummaryRowDTO> summaries = new ArrayList<>();
    private final List<LoopBlockDTO> loopBlocks = new ArrayList<>();
    private List<CellData> templateCells = List.of();
    private int colCount;
    private int rowCount;

    public ReportConfigBuilder(String name) {
        this.name = name;
    }

    // ─── 链式：单元格绑定 ───

    public ReportConfigBuilder binding(int row, int col, ValueDTO value, String expansion, String expandMode) {
        return binding(row, col, value, expansion, expandMode, false, null);
    }

    public ReportConfigBuilder binding(int row, int col, ValueDTO value, String expansion, String expandMode,
                                       boolean mergeRepeated) {
        return binding(row, col, value, expansion, expandMode, mergeRepeated, null);
    }

    public ReportConfigBuilder binding(int row, int col, ValueDTO value, String expansion, String expandMode,
                                       boolean mergeRepeated, String parentCell) {
        bindings.add(makeBinding(row, col, value, expansion, expandMode, mergeRepeated, parentCell, List.of()));
        return this;
    }

    public ReportConfigBuilder bindingWithConditions(int row, int col, ValueDTO value,
                                                     String expansion, String expandMode, ConditionDTO condition) {
        // 条件附带唯一 id
        ConditionDTO c = new ConditionDTO("c_" + row + "_" + col, condition.left(), condition.operator(), condition.right());
        bindings.add(makeBinding(row, col, value, expansion, expandMode, false, null, List.of(c)));
        return this;
    }

    private BindingDTO makeBinding(int row, int col, ValueDTO value, String expansion, String expandMode,
                                   boolean mergeRepeated, String parentCell, List<ConditionDTO> conditions) {
        return new BindingDTO(cellKey(row, col), value, expansion, expandMode, mergeRepeated, parentCell, conditions,
                false, null, false, null);
    }

    // ─── 链式：汇总行 ───

    public ReportConfigBuilder summary(int row, int fromColumn, int toColumn,
                                       FieldRefDTO groupBy, List<SummaryCellDTO> cells) {
        summaries.add(new SummaryRowDTO("sum-" + System.nanoTime(), groupBy, fromColumn, toColumn,
                List.copyOf(cells), row));
        return this;
    }

    // ─── 链式：循环块 ───

    public ReportConfigBuilder loopBlock(LoopBlockDTO block) {
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

    public ReportConfig build() {
        ReportConfig rc = new ReportConfig();
        rc.setName(name);
        rc.setDataModelId("default");
        rc.setCellBindings(List.copyOf(bindings));
        rc.setSummaries(List.copyOf(summaries));
        rc.setLoopBlocks(List.copyOf(loopBlocks));
        rc.setParams(List.of());
        rc.setTemplate(buildWorkbook(templateCells, colCount, rowCount));
        return rc;
    }

    // ============================================================
    // 静态值工厂
    // ============================================================

    public static String cellKey(int row, int col) {
        return SHEET + ":" + row + ":" + col;
    }

    public static ValueDTO literal(String text) {
        return new ValueDTO("Literal", text, null, null, null, null, null);
    }

    public static ValueDTO fieldValue(String datasetId, String field) {
        return new ValueDTO("FieldValue", datasetId + "." + field, null, null, null, null, null);
    }

    public static ValueDTO aggregate(String agg, String datasetId, String field) {
        return new ValueDTO("Aggregate", null, agg, fieldValue(datasetId, field), null, null, null);
    }

    public static SummaryCellDTO labelCell(int column, String label) {
        ValueDTO value = label.contains("${") ? buildTemplateValue(label) : literal(label);
        return new SummaryCellDTO(column, value, null, null, null, null, false, null);
    }

    public static SummaryCellDTO aggCell(int column, String payload, String aggregation) {
        ValueDTO value = new ValueDTO("Aggregate", null, aggregation,
                new ValueDTO("FieldValue", payload, null, null, null, null, null),
                null, null, null);
        return new SummaryCellDTO(column, value, null, null, aggregation, null, false, null);
    }

    /** 构造 Template ValueDTO：支持 ${name} 占位符（编译为 NameRef） */
    public static ValueDTO buildTemplateValue(String text) {
        List<PartDTO> parts = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int start = text.indexOf("${", i);
            if (start == -1) {
                parts.add(new PartDTO("text", text.substring(i), null));
                break;
            }
            if (start > i) {
                parts.add(new PartDTO("text", text.substring(i, start), null));
            }
            int end = text.indexOf("}", start + 2);
            String name = text.substring(start + 2, end);
            parts.add(new PartDTO("hole", null, new ValueDTO("NameRef", name, null, null, null, null, null)));
            i = end + 1;
        }
        // 整个字符串就是一个洞 → 直接返回洞内 ValueDTO
        if (parts.size() == 1 && "hole".equals(parts.get(0).kind())) {
            return parts.get(0).value();
        }
        return new ValueDTO("Template", null, null, null, null, null, List.copyOf(parts));
    }

    public static CellData cell(int row, int col, String value) {
        return new CellData(row, col, value);
    }

    public record CellData(int row, int col, String value) {
    }

    // ─── Workbook 构建 ───

    private static Workbook buildWorkbook(List<CellData> cells, int colCount, int rowCount) {
        List<Cell> excelCells = new ArrayList<>();
        for (CellData c : cells) {
            Cell cell = new Cell();
            cell.setRow(c.row());
            cell.setCol(c.col());
            cell.setRef(CellRefs.toRef(c.row(), c.col()));
            cell.setValue(TextNode.valueOf(c.value()));
            excelCells.add(cell);
        }

        Sheet sheet = new Sheet();
        sheet.setId(SHEET);
        sheet.setName("Sheet1");
        sheet.setRowCount(rowCount);
        sheet.setColumnCount(colCount);
        sheet.setDefaultRowHeight(25);
        sheet.setDefaultColumnWidth(100);
        sheet.setMerges(List.of());
        sheet.setCells(excelCells);
        sheet.setRows(List.of());
        sheet.setColumns(List.of());

        Workbook workbook = new Workbook();
        workbook.setSheets(List.of(sheet));
        return workbook;
    }
}
