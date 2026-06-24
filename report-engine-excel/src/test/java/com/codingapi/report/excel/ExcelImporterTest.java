package com.codingapi.report.excel;

import static org.junit.jupiter.api.Assertions.*;

import com.codingapi.report.excel.pojo.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Excel 导入器测试 — 验证 export → import 的 round-trip 正确性。 先用 ExcelExporter 将 JSON 构建为 .xlsx，再用
 * ExcelImporter 解析回 Workbook， 对比关键字段确认数据完整性。
 */
class ExcelImporterTest {

    private static Workbook original;
    private static Workbook parsed;

    @BeforeAll
    static void setUp() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InputStream is = ExcelImporterTest.class.getResourceAsStream("/test-workbook.json");
        original = mapper.readValue(is, Workbook.class);

        ExcelExporter exporter = new ExcelExporter();
        byte[] xlsxBytes = exporter.export(original);

        ExcelImporter importer = new ExcelImporter();
        parsed = importer.importFrom(xlsxBytes);

        assertNotNull(parsed);
        assertNotNull(parsed.getSheets());
    }

    @Test
    void sheetCount() {
        assertEquals(original.getSheets().size(), parsed.getSheets().size());
    }

    @Test
    void sheetNames() {
        assertEquals("样式测试", parsed.getSheets().get(0).getName());
        assertEquals("尺寸测试", parsed.getSheets().get(1).getName());
    }

    @Test
    void cellValues_stringAndNumber() {
        Sheet sheet = parsed.getSheets().get(0);
        Cell titleCell = findCell(sheet, 0, 0);
        assertNotNull(titleCell);
        assertEquals("报表样式验证", titleCell.getValue().textValue());

        Cell numCell = findCell(sheet, 2, 1);
        assertNotNull(numCell);
        assertEquals(3.14159, numCell.getValue().doubleValue(), 0.001);

        Cell boolCell = findCell(sheet, 2, 2);
        assertNotNull(boolCell);
        assertTrue(boolCell.getValue().booleanValue());
    }

    @Test
    void formula() {
        Sheet sheet = parsed.getSheets().get(0);
        Cell formulaCell = findCell(sheet, 2, 4);
        assertNotNull(formulaCell);
        assertNotNull(formulaCell.getFormula());
        assertEquals("SUM(B3:B3)", formulaCell.getFormula());
    }

    @Test
    void fontStyle_boldAndItalic() {
        Sheet sheet = parsed.getSheets().get(0);

        // 标题 bold
        Cell titleCell = findCell(sheet, 0, 0);
        assertNotNull(titleCell.getStyle());
        assertNotNull(titleCell.getStyle().getFont());
        assertTrue(titleCell.getStyle().getFont().getBold());

        // A3 italic
        Cell a3 = findCell(sheet, 2, 0);
        assertNotNull(a3.getStyle());
        assertNotNull(a3.getStyle().getFont());
        assertTrue(a3.getStyle().getFont().getItalic());
    }

    @Test
    void backgroundFill() {
        Sheet sheet = parsed.getSheets().get(0);
        Cell titleCell = findCell(sheet, 0, 0);
        assertNotNull(titleCell.getStyle());
        assertNotNull(titleCell.getStyle().getFill());
        // 颜色 round-trip 应保持 #1F4E79
        assertEquals("#1F4E79", titleCell.getStyle().getFill());
    }

    @Test
    void alignment() {
        Sheet sheet = parsed.getSheets().get(0);

        Cell a5 = findCell(sheet, 4, 0);
        assertNotNull(a5.getStyle());
        assertEquals("left", a5.getStyle().getAlign());
        assertEquals("top", a5.getStyle().getValign());

        Cell b5 = findCell(sheet, 4, 1);
        assertNotNull(b5.getStyle());
        assertEquals("center", b5.getStyle().getAlign());
        assertEquals("middle", b5.getStyle().getValign());
    }

    @Test
    void wrapText() {
        Sheet sheet = parsed.getSheets().get(0);
        Cell a6 = findCell(sheet, 5, 0);
        assertNotNull(a6.getStyle());
        assertTrue(a6.getStyle().getWrap());
    }

    @Test
    void rotation() {
        Sheet sheet = parsed.getSheets().get(0);
        Cell b6 = findCell(sheet, 5, 1);
        assertNotNull(b6.getStyle());
        assertEquals(45, b6.getStyle().getRotation());
    }

    @Test
    void borderStyles_thinAndThick() {
        Sheet sheet = parsed.getSheets().get(0);

        // A9: thin 边框
        Cell a9 = findCell(sheet, 8, 0);
        assertNotNull(a9.getStyle());
        assertNotNull(a9.getStyle().getBorders());
        assertEquals("thin", a9.getStyle().getBorders().getTop().getStyle());

        // A11: thick 边框
        Cell a11 = findCell(sheet, 10, 0);
        assertNotNull(a11.getStyle());
        assertNotNull(a11.getStyle().getBorders());
        assertEquals("thick", a11.getStyle().getBorders().getTop().getStyle());
    }

    @Test
    void numberFormat() {
        Sheet sheet = parsed.getSheets().get(0);
        Cell b3 = findCell(sheet, 2, 1);
        assertNotNull(b3.getStyle());
        assertEquals("0.00", b3.getStyle().getNumberFormat());
    }

    @Test
    void merges() {
        Sheet sheet = parsed.getSheets().get(0);
        assertNotNull(sheet.getMerges());
        assertEquals(1, sheet.getMerges().size());

        Merge merge = sheet.getMerges().get(0);
        assertEquals(0, merge.getStartRow());
        assertEquals(0, merge.getStartCol());
        assertEquals(1, merge.getRowSpan());
        assertEquals(6, merge.getColSpan());
    }

    @Test
    void customRowHeights() {
        Sheet sheet = parsed.getSheets().get(1);
        assertNotNull(sheet.getRows());
        assertTrue(sheet.getRows().size() > 0);

        // Row 0: 60px → 45pt → ~60px (round-trip)
        Row row0 = sheet.getRows().stream().filter(r -> r.getIndex() == 0).findFirst().orElse(null);
        assertNotNull(row0);
        assertEquals(60.0, row0.getHeight(), 1.0);
    }

    @Test
    void customColumnWidths() {
        Sheet sheet = parsed.getSheets().get(1);
        assertNotNull(sheet.getColumns());

        // Column 0: 200px width
        Column col0 =
                sheet.getColumns().stream().filter(c -> c.getIndex() == 0).findFirst().orElse(null);
        assertNotNull(col0, "Column 0 should have custom width");
        assertEquals(200.0, col0.getWidth(), 5.0);

        // Column 1: 60px width
        Column col1 =
                sheet.getColumns().stream().filter(c -> c.getIndex() == 1).findFirst().orElse(null);
        assertNotNull(col1, "Column 1 should have custom width");
        assertEquals(60.0, col1.getWidth(), 5.0);
    }

    @Test
    void richText_roundTrip() {
        Sheet sheet = parsed.getSheets().get(0);
        Cell f3 = findCell(sheet, 2, 5);
        assertNotNull(f3);
        assertNotNull(f3.getRichText(), "Rich text should survive round-trip");
        assertEquals("红色粗体+蓝色斜体", f3.getRichText().getText());
        assertNotNull(f3.getRichText().getSegments());
        assertTrue(f3.getRichText().getSegments().size() >= 3);

        // 第一段应该是粗体
        RichTextSegment seg0 = f3.getRichText().getSegments().get(0);
        assertNotNull(seg0.getStyle());
        assertTrue(seg0.getStyle().getBold());
    }

    // ─── 辅助方法 ─────────────────────────────────────────────

    private static Cell findCell(Sheet sheet, int row, int col) {
        if (sheet.getCells() == null) return null;
        return sheet.getCells().stream()
                .filter(c -> c.getRow() == row && c.getCol() == col)
                .findFirst()
                .orElse(null);
    }
}
