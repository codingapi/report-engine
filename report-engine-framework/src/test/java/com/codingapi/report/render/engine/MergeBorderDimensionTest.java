package com.codingapi.report.render.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.data.dataset.DataType;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.DataSourceType;
import com.codingapi.report.data.datasource.csv.CsvDataExtractor;
import com.codingapi.report.excel.ExcelExporter;
import com.codingapi.report.excel.ExcelImporter;
import com.codingapi.report.excel.pojo.Border;
import com.codingapi.report.excel.pojo.Borders;
import com.codingapi.report.excel.pojo.Cell;
import com.codingapi.report.excel.pojo.Column;
import com.codingapi.report.excel.pojo.Merge;
import com.codingapi.report.excel.pojo.Row;
import com.codingapi.report.excel.pojo.Sheet;
import com.codingapi.report.excel.pojo.Style;
import com.codingapi.report.excel.pojo.Workbook;
import com.codingapi.report.expression.Value;
import com.codingapi.report.param.ParamContext;
import com.codingapi.report.render.Report;
import com.codingapi.report.render.grid.CellBinding;
import com.codingapi.report.render.grid.CellRef;
import com.codingapi.report.render.grid.ExpandMode;
import com.codingapi.report.render.grid.Expansion;
import com.codingapi.report.render.grid.SummaryCell;
import com.codingapi.report.render.grid.SummaryRow;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 复现用户报表配置（人员花名册）的两类导出适配问题并验证修复：
 *
 * <ol>
 *   <li>底部合并区（备注 A5:C7）只在锚点格描边 → 导出后整个合并区周边应有完整边框
 *   <li>模板的列宽/行高（及随带扩展的位移）在 render 后被丢弃 → 导出应保留
 * </ol>
 */
class MergeBorderDimensionTest {

    private static final JsonNodeFactory NF = JsonNodeFactory.instance;

    @Test
    @DisplayName("合并区边框补全 + 行高列宽带出：导出回读后周边边框完整、列宽保留、行高随带下移")
    void mergeBordersAndDimensionsSurvive() throws Exception {
        DataSource src =
                DataSource.builder()
                        .id("ds_a")
                        .name("a")
                        .type(DataSourceType.CSV)
                        .config(Map.of("path", "/data/dept_a.csv"))
                        .build();
        Dataset a =
                TableDataset.builder()
                        .id("dept_a")
                        .datasourceId("ds_a")
                        .sourceTable("dept_a.csv")
                        .fields(
                                List.of(
                                        Field.builder()
                                                .name("name")
                                                .dataType(DataType.STRING)
                                                .build(),
                                        Field.builder()
                                                .name("gender")
                                                .dataType(DataType.STRING)
                                                .build(),
                                        Field.builder()
                                                .name("age")
                                                .dataType(DataType.NUMBER)
                                                .build()))
                        .build();
        DataModel dm =
                DataModel.builder()
                        .id("default")
                        .name("m")
                        .datasources(List.of(src))
                        .datasets(List.of(a))
                        .relationships(List.of())
                        .build();

        SummaryRow sum =
                SummaryRow.builder()
                        .mainPos(3)
                        .groupBy(null)
                        .crossFrom(0)
                        .crossTo(2)
                        .cells(
                                List.of(
                                        new SummaryCell(0, new Value.Literal("总人数")),
                                        new SummaryCell(1, new Value.Literal("")),
                                        new SummaryCell(
                                                2,
                                                new Value.Aggregate(
                                                        "COUNT",
                                                        new Value.FieldValue(
                                                                new FieldRef("dept_a", "name"))))))
                        .build();

        Report report =
                Report.builder()
                        .id("r")
                        .name("未命名")
                        .dataModelId("default")
                        .cellBindings(
                                List.of(
                                        bind(2, 0, "name"),
                                        bind(2, 1, "gender"),
                                        bind(2, 2, "age")))
                        .loopBlocks(List.of())
                        .summaries(List.of(sum))
                        .build();

        Sheet ts = new Sheet();
        ts.setId("E66v9lU4abdfCkdpHMnK2");
        ts.setName("Sheet1");
        ts.setRowCount(1000);
        ts.setColumnCount(20);
        ts.setDefaultRowHeight(24);
        ts.setDefaultColumnWidth(88);
        ts.setMerges(
                new ArrayList<>(
                        List.of(
                                merge(0, 0, 1, 3), // 标题
                                merge(3, 0, 1, 2), // 汇总 总人数 跨 A:B
                                merge(4, 0, 3, 3)))); // 底部备注 A5:C7
        ts.setCells(
                List.of(
                        cell(0, 0, "人员花名册", borders()),
                        cell(1, 0, "姓名", borders()),
                        cell(1, 1, "性别", borders()),
                        cell(1, 2, "年龄", borders()),
                        cell(2, 0, "姓名", borders()),
                        cell(2, 1, "性别", borders()),
                        cell(2, 2, "年龄", borders()),
                        cell(3, 0, "总人数", borders()),
                        cell(3, 2, "${COUNT(姓名)}", borders()),
                        cell(4, 0, "备注：你好", borders())));
        // 列宽：col1=98, col2=120
        ts.setColumns(List.of(column(1, 98), column(2, 120)));
        // 行高：底部备注行（设计 row 4）自定义高度 60，渲染后应随带扩展移到输出 row 5
        Row footerRow = new Row();
        footerRow.setIndex(4);
        footerRow.setHeight(60);
        ts.setRows(new ArrayList<>(List.of(footerRow)));
        Workbook tpl = new Workbook();
        tpl.setSheets(List.of(ts));

        ReportRenderer renderer = new ReportRenderer(List.of(new CsvDataExtractor()));
        Workbook out = renderer.render(dm, report, new ParamContext(Map.of()), tpl);
        Sheet sheet =
                new ExcelImporter().importFrom(new ExcelExporter().export(out)).getSheets().get(0);

        // ---- 问题1回归：汇总 总人数 仍在 row4 ----
        assertEquals("总人数", textAt(sheet, 4, 0));
        assertEquals(2.0, numberAt(sheet, 4, 2), 0.0001);
        // ---- 问题1回归：备注随带下移到 row5（A6 起的合并区）----
        assertEquals("备注：你好", textAt(sheet, 5, 0));

        // ---- 问题2：底部合并区周边边框完整 ----
        // 合并区输出为 row5..7 / col0..2；右下角格 (7,2) 应有 下/右 边框
        Cell br = cellAt(sheet, 7, 2);
        assertNotNull(br, "合并区右下角格应被补出");
        assertNotNull(br.getStyle().getBorders().getBottom(), "合并区底边应铺到右下角格");
        assertNotNull(br.getStyle().getBorders().getRight(), "合并区右边应铺到右下角格");
        // 左下角格 (7,0) 应有 下/左 边框
        Cell bl = cellAt(sheet, 7, 0);
        assertNotNull(bl.getStyle().getBorders().getBottom(), "合并区底边应铺到左下角格");
        assertNotNull(bl.getStyle().getBorders().getLeft(), "合并区左边应铺到左下角格");

        // ---- 问题3：列宽保留 ----
        Column c1 = columnAt(sheet, 1);
        Column c2 = columnAt(sheet, 2);
        assertNotNull(c1, "col1 列宽应保留");
        assertEquals(98.0, c1.getWidth(), 1.5);
        assertNotNull(c2, "col2 列宽应保留");
        assertEquals(120.0, c2.getWidth(), 1.5);

        // ---- 问题3：行高随带下移到 row5 ----
        Row r5 = rowAt(sheet, 5);
        assertNotNull(r5, "备注行的自定义行高应随带扩展下移到 row5");
        assertEquals(60.0, r5.getHeight(), 1.0);
    }

    // ---- 构造小工具 ----

    private static CellBinding bind(int r, int col, String field) {
        return CellBinding.builder()
                .cell(new CellRef("E66v9lU4abdfCkdpHMnK2", r, col))
                .value(new Value.FieldValue(new FieldRef("dept_a", field)))
                .expansion(Expansion.VERTICAL)
                .expandMode(ExpandMode.LIST)
                .build();
    }

    private static Border border() {
        Border b = new Border();
        b.setStyle("thin");
        b.setColor("#000000");
        return b;
    }

    private static Style borders() {
        Borders bs = new Borders();
        bs.setTop(border());
        bs.setRight(border());
        bs.setBottom(border());
        bs.setLeft(border());
        Style s = new Style();
        s.setBorders(bs);
        return s;
    }

    private static Cell cell(int r, int col, String v, Style s) {
        Cell c = new Cell();
        c.setRow(r);
        c.setCol(col);
        c.setValue(NF.textNode(v));
        c.setStyle(s);
        return c;
    }

    private static Merge merge(int r, int col, int rs, int cs) {
        Merge m = new Merge();
        m.setStartRow(r);
        m.setStartCol(col);
        m.setRowSpan(rs);
        m.setColSpan(cs);
        return m;
    }

    private static Column column(int index, double width) {
        Column c = new Column();
        c.setIndex(index);
        c.setWidth(width);
        return c;
    }

    private static Cell cellAt(Sheet sheet, int row, int col) {
        return sheet.getCells() == null
                ? null
                : sheet.getCells().stream()
                        .filter(c -> c.getRow() == row && c.getCol() == col)
                        .findFirst()
                        .orElse(null);
    }

    private static String textAt(Sheet sheet, int row, int col) {
        Cell c = cellAt(sheet, row, col);
        assertNotNull(c, "缺少单元格 (" + row + "," + col + ")");
        return c.getValue().asText();
    }

    private static Double numberAt(Sheet sheet, int row, int col) {
        Cell c = cellAt(sheet, row, col);
        assertNotNull(c, "缺少单元格 (" + row + "," + col + ")");
        return c.getValue().asDouble();
    }

    private static Column columnAt(Sheet sheet, int index) {
        return sheet.getColumns() == null
                ? null
                : sheet.getColumns().stream()
                        .filter(c -> c.getIndex() == index)
                        .findFirst()
                        .orElse(null);
    }

    private static Row rowAt(Sheet sheet, int index) {
        return sheet.getRows() == null
                ? null
                : sheet.getRows().stream()
                        .filter(r -> r.getIndex() == index)
                        .findFirst()
                        .orElse(null);
    }
}
