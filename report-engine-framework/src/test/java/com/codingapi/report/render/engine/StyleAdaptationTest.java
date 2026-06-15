package com.codingapi.report.render.engine;

import com.codingapi.report.param.ParamContext;
import com.codingapi.report.data.datasource.csv.CsvDataExtractor;

import com.codingapi.report.excel.ExcelExporter;
import com.codingapi.report.excel.ExcelImporter;
import com.codingapi.report.excel.pojo.Border;
import com.codingapi.report.excel.pojo.Borders;
import com.codingapi.report.excel.pojo.Cell;
import com.codingapi.report.excel.pojo.Font;
import com.codingapi.report.excel.pojo.RichText;
import com.codingapi.report.excel.pojo.RichTextSegment;
import com.codingapi.report.excel.pojo.Sheet;
import com.codingapi.report.excel.pojo.Style;
import com.codingapi.report.excel.pojo.Workbook;
import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.render.Report;
import com.codingapi.report.render.grid.CellBinding;
import com.codingapi.report.render.grid.CellRef;
import com.codingapi.report.render.grid.ExpandMode;
import com.codingapi.report.render.grid.Expansion;
import com.codingapi.report.render.grid.FieldCell;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.DataSourceType;
import com.codingapi.report.data.dataset.DataType;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.dataset.FieldRef;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 样式适配全链路：模板（Univer 画布）携带<b>合并标题 + 富文本 + 加粗表头 + 边框</b>，
 * 渲染器以模板为底填入列表数据并<b>保留样式</b>，导出本地 xlsx 后回读校验样式存活。
 *
 * <p>验证三件事：① 合并的标题单元格；② 富文本分段样式；③ 表头/数据的边框，
 * 以及"扩展行继承声明格样式"（数据多行都带边框）。
 */
class StyleAdaptationTest {

    private static final JsonNodeFactory NF = JsonNodeFactory.instance;

    @Test
    @DisplayName("样式适配：模板的合并标题/富文本/边框，经渲染+导出+回读后保留")
    void styleSurvivesRenderExportReimport() throws Exception {
        DataModel dm = staffModel();
        Report report = staffListReport();
        Workbook template = styledTemplate();

        // 渲染：以模板为底，填入列表数据并保留样式
        ReportRenderer renderer = new ReportRenderer(List.of(new CsvDataExtractor()));
        Workbook workbook = renderer.render(dm, report, new ParamContext(Map.of()), template);

        // 导出到本地文件 + 回读
        Path dir = Path.of("target", "reports");
        Files.createDirectories(dir);
        Path file = dir.resolve("styled.xlsx");
        Files.write(file, new ExcelExporter().export(workbook));
        System.out.println("[report] 导出: " + file.toAbsolutePath());
        Sheet sheet = new ExcelImporter().importFrom(Files.readAllBytes(file)).getSheets().get(0);

        // ① 合并标题：跨 3 列合并
        assertTrue(sheet.getMerges() != null && sheet.getMerges().stream().anyMatch(m ->
                        m.getStartRow() == 0 && m.getStartCol() == 0 && m.getColSpan() == 3),
                "标题应跨 3 列合并");

        // ② 富文本：标题是分段富文本，"员工"加粗
        Cell titleCell = cell(sheet, 0, 0);
        RichText rt = titleCell.getRichText();
        assertNotNull(rt, "标题应为富文本");
        assertEquals("员工名单", rt.getText());
        assertTrue(rt.getSegments().size() >= 2, "富文本应有多个分段");
        assertTrue(rt.getSegments().stream().anyMatch(s -> Boolean.TRUE.equals(s.getStyle().getBold())),
                "富文本应有加粗分段");

        // ③ 表头：加粗 + 四边边框
        Cell header = cell(sheet, 1, 0);
        assertEquals("工号", header.getValue().asText());
        assertEquals(Boolean.TRUE, header.getStyle().getFont().getBold(), "表头应加粗");
        assertNotNull(header.getStyle().getBorders().getBottom(), "表头应有下边框");

        // 数据：值正确，且每行（含扩展出来的行）都继承了声明格的边框
        assertEquals(1001.0, cell(sheet, 2, 0).getValue().asDouble(), 0.0001);
        assertEquals("张三", cell(sheet, 2, 1).getValue().asText());
        assertEquals("李四", cell(sheet, 3, 1).getValue().asText());
        assertEquals("王五", cell(sheet, 4, 1).getValue().asText());
        assertNotNull(cell(sheet, 2, 0).getStyle().getBorders().getBottom(), "首个数据格应有边框");
        assertNotNull(cell(sheet, 4, 0).getStyle().getBorders().getBottom(), "扩展出来的数据行也应继承边框");
    }

    // ---- 数据模型 / 报表 ----

    private static DataModel staffModel() {
        DataSource src = DataSource.builder().id("ds").name("员工CSV").type(DataSourceType.CSV)
                .config(Map.of("path", "/data/styled_staff.csv")).build();
        Dataset staff = TableDataset.builder().id("d_staff").datasourceId("ds").sourceTable("styled_staff.csv")
                .fields(List.of(
                        Field.builder().name("id").dataType(DataType.NUMBER).build(),
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("dept").dataType(DataType.STRING).build()))
                .build();
        return DataModel.builder().id("dm").name("员工模型")
                .datasources(List.of(src)).datasets(List.of(staff)).relationships(List.of()).build();
    }

    /** 列表报表：数据从第 2 行起，列 工号/姓名/部门（只绑数据，样式由模板提供） */
    private static Report staffListReport() {
        FieldCell idCol = listCol(2, 0, "id");
        FieldCell nameCol = listCol(2, 1, "name");
        FieldCell deptCol = listCol(2, 2, "dept");
        return Report.builder().id("r").name("员工名单").dataModelId("dm").templateId("tpl")
                .parameters(List.of())
                .cellBindings(List.<CellBinding>of(idCol, nameCol, deptCol))
                .loopBlocks(List.of())
                .build();
    }

    private static FieldCell listCol(int row, int col, String field) {
        return FieldCell.builder().cell(new CellRef("sheet1", row, col)).field(new FieldRef("d_staff", field))
                .expansion(Expansion.VERTICAL).expandMode(ExpandMode.LIST).build();
    }

    // ---- 模板画布（携带样式/边框/合并/富文本）----

    private static Workbook styledTemplate() {
        Sheet sheet = new Sheet();
        sheet.setId("sheet1");
        sheet.setName("Sheet1");

        // 标题(0,0)：富文本"员工名单"（员工=加粗红，名单=蓝），跨 3 列合并
        RichText rt = new RichText();
        rt.setText("员工名单");
        rt.setSegments(List.of(
                segment("员工", font(true, "FF0000", 14.0)),
                segment("名单", font(false, "0000FF", 14.0))));
        Cell title = new Cell();
        title.setRow(0);
        title.setCol(0);
        title.setRichText(rt);

        com.codingapi.report.excel.pojo.Merge merge = new com.codingapi.report.excel.pojo.Merge();
        merge.setStartRow(0);
        merge.setStartCol(0);
        merge.setRowSpan(1);
        merge.setColSpan(3);

        // 表头(1,*)：加粗 + 四边边框
        Style headerStyle = style(font(true, "000000", 12.0), allBorders());
        Cell h0 = textCell(1, 0, "工号", headerStyle);
        Cell h1 = textCell(1, 1, "姓名", headerStyle);
        Cell h2 = textCell(1, 2, "部门", headerStyle);

        // 数据声明格(2,*)：仅四边边框（值由渲染填入，扩展行继承此样式）
        Style dataStyle = style(null, allBorders());
        Cell d0 = styledProto(2, 0, dataStyle);
        Cell d1 = styledProto(2, 1, dataStyle);
        Cell d2 = styledProto(2, 2, dataStyle);

        sheet.setCells(List.of(title, h0, h1, h2, d0, d1, d2));
        sheet.setMerges(List.of(merge));

        Workbook wb = new Workbook();
        wb.setSheets(List.of(sheet));
        return wb;
    }

    // ---- 样式构造小工具 ----

    private static Font font(boolean bold, String color, double size) {
        Font f = new Font();
        f.setBold(bold);
        f.setColor(color);
        f.setSize(size);
        return f;
    }

    private static Borders allBorders() {
        Border b = new Border();
        b.setStyle("thin");
        b.setColor("000000");
        Borders bs = new Borders();
        bs.setTop(b);
        bs.setRight(b);
        bs.setBottom(b);
        bs.setLeft(b);
        return bs;
    }

    private static Style style(Font font, Borders borders) {
        Style s = new Style();
        s.setFont(font);
        s.setBorders(borders);
        return s;
    }

    private static RichTextSegment segment(String text, Font style) {
        RichTextSegment seg = new RichTextSegment();
        seg.setText(text);
        seg.setStyle(style);
        return seg;
    }

    private static Cell textCell(int row, int col, String value, Style style) {
        Cell c = new Cell();
        c.setRow(row);
        c.setCol(col);
        c.setValue(NF.textNode(value));
        c.setStyle(style);
        return c;
    }

    private static Cell styledProto(int row, int col, Style style) {
        Cell c = new Cell();
        c.setRow(row);
        c.setCol(col);
        c.setStyle(style);
        return c;
    }

    private static Cell cell(Sheet sheet, int row, int col) {
        Cell c = sheet.getCells().stream()
                .filter(x -> x.getRow() == row && x.getCol() == col).findFirst().orElse(null);
        assertNotNull(c, "缺少单元格 (" + row + "," + col + ")");
        return c;
    }
}
