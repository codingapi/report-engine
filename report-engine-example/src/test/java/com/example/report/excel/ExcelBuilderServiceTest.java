package com.example.report.excel;

import com.example.report.excel.dto.ExcelWorkbookDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ExcelBuilderServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static ExcelWorkbookDTO workbookDTO;
    private static byte[] xlsxBytes;

    @BeforeAll
    static void setUp() throws Exception {
        InputStream is = ExcelBuilderServiceTest.class.getResourceAsStream("/test-workbook.json");
        assertNotNull(is, "test-workbook.json not found in test resources");

        workbookDTO = MAPPER.readValue(is, ExcelWorkbookDTO.class);
        assertNotNull(workbookDTO.getSheets());
        assertEquals(2, workbookDTO.getSheets().size());

        ExcelBuilderService service = new ExcelBuilderService();
        xlsxBytes = service.buildExcel(workbookDTO);
        assertNotNull(xlsxBytes);
        assertTrue(xlsxBytes.length > 0);
    }

    @Test
    void generatedFileCanBeOpenedByPoi() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            assertEquals(2, wb.getNumberOfSheets());
        }
    }

    // ─── Sheet 1: 样式测试 ──────────────────────────────────

    @Test
    void sheet1_nameAndStructure() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("样式测试", sheet.getSheetName());

            // 合并区域：A1:F1
            assertEquals(1, sheet.getNumMergedRegions());
            CellRangeAddress merge = sheet.getMergedRegion(0);
            assertEquals(0, merge.getFirstRow());
            assertEquals(0, merge.getFirstColumn());
            assertEquals(0, merge.getLastRow());
            assertEquals(5, merge.getLastColumn());
        }
    }

    @Test
    void sheet1_cellValues() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);

            // 标题值
            assertEquals("报表样式验证", sheet.getRow(0).getCell(0).getStringCellValue());

            // 字符串值
            assertEquals("Hello World", sheet.getRow(2).getCell(0).getStringCellValue());

            // 数字值
            assertEquals(3.14159, sheet.getRow(2).getCell(1).getNumericCellValue(), 0.00001);

            // 布尔值
            assertTrue(sheet.getRow(2).getCell(2).getBooleanCellValue());

            // 空单元格
            Row emptyRow = sheet.getRow(6);
            assertTrue(emptyRow == null || emptyRow.getCell(0) == null);
        }
    }

    @Test
    void sheet1_formula() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);
            Cell formulaCell = sheet.getRow(2).getCell(4);
            assertNotNull(formulaCell);
            assertEquals(CellType.FORMULA, formulaCell.getCellType());
            assertEquals("SUM(B3:B3)", formulaCell.getCellFormula());
        }
    }

    @Test
    void sheet1_fontStyles() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);

            // 标题字体：bold, size=18, color=#FFFFFF
            Cell titleCell = sheet.getRow(0).getCell(0);
            Font titleFont = wb.getFontAt(titleCell.getCellStyle().getFontIndex());
            assertTrue(titleFont.getBold());
            assertEquals(18, titleFont.getFontHeightInPoints());
            assertColorEquals("#FFFFFF", (XSSFFont) titleFont);

            // A3: italic, color=#1F4E79
            Cell a3 = sheet.getRow(2).getCell(0);
            Font a3Font = wb.getFontAt(a3.getCellStyle().getFontIndex());
            assertTrue(a3Font.getItalic());
            assertColorEquals("#1F4E79", (XSSFFont) a3Font);
            assertEquals("Arial", a3Font.getFontName());

            // A8: underline
            Cell a8 = sheet.getRow(7).getCell(0);
            Font a8Font = wb.getFontAt(a8.getCellStyle().getFontIndex());
            assertTrue(a8Font.getUnderline() != 0);

            // B8: strikethrough
            Cell b8 = sheet.getRow(7).getCell(1);
            Font b8Font = wb.getFontAt(b8.getCellStyle().getFontIndex());
            assertTrue(b8Font.getStrikeout());

            // C8: bold + italic + Times New Roman
            Cell c8 = sheet.getRow(7).getCell(2);
            Font c8Font = wb.getFontAt(c8.getCellStyle().getFontIndex());
            assertTrue(c8Font.getBold());
            assertTrue(c8Font.getItalic());
            assertEquals("Times New Roman", c8Font.getFontName());
            assertColorEquals("#7030A0", (XSSFFont) c8Font);
        }
    }

    @Test
    void sheet1_backgroundFill() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);

            // 标题背景 #1F4E79
            Cell titleCell = sheet.getRow(0).getCell(0);
            XSSFCellStyle titleStyle = (XSSFCellStyle) titleCell.getCellStyle();
            assertColorEquals("#1F4E79", titleStyle.getFillForegroundXSSFColor());
            assertEquals(FillPatternType.SOLID_FOREGROUND, titleStyle.getFillPattern());

            // 表头背景 #D6E4F0
            Cell headerCell = sheet.getRow(1).getCell(0);
            XSSFCellStyle headerStyle = (XSSFCellStyle) headerCell.getCellStyle();
            assertColorEquals("#D6E4F0", headerStyle.getFillForegroundXSSFColor());

            // 对齐测试行背景 #FFF2CC
            Cell alignCell = sheet.getRow(4).getCell(0);
            XSSFCellStyle alignStyle = (XSSFCellStyle) alignCell.getCellStyle();
            assertColorEquals("#FFF2CC", alignStyle.getFillForegroundXSSFColor());
        }
    }

    @Test
    void sheet1_alignment() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);

            // 标题: center + middle
            CellStyle titleStyle = sheet.getRow(0).getCell(0).getCellStyle();
            assertEquals(HorizontalAlignment.CENTER, titleStyle.getAlignment());
            assertEquals(VerticalAlignment.CENTER, titleStyle.getVerticalAlignment());

            // A5: left + top
            CellStyle a5Style = sheet.getRow(4).getCell(0).getCellStyle();
            assertEquals(HorizontalAlignment.LEFT, a5Style.getAlignment());
            assertEquals(VerticalAlignment.TOP, a5Style.getVerticalAlignment());

            // B5: center + middle
            CellStyle b5Style = sheet.getRow(4).getCell(1).getCellStyle();
            assertEquals(HorizontalAlignment.CENTER, b5Style.getAlignment());
            assertEquals(VerticalAlignment.CENTER, b5Style.getVerticalAlignment());

            // C5: right + bottom
            CellStyle c5Style = sheet.getRow(4).getCell(2).getCellStyle();
            assertEquals(HorizontalAlignment.RIGHT, c5Style.getAlignment());
            assertEquals(VerticalAlignment.BOTTOM, c5Style.getVerticalAlignment());
        }
    }

    @Test
    void sheet1_wrapAndRotation() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);

            // A6: wrap text
            CellStyle a6Style = sheet.getRow(5).getCell(0).getCellStyle();
            assertTrue(a6Style.getWrapText());

            // B6: rotation 45°
            CellStyle b6Style = sheet.getRow(5).getCell(1).getCellStyle();
            assertEquals(45, b6Style.getRotation());

            // C6: rotation 90°
            CellStyle c6Style = sheet.getRow(5).getCell(2).getCellStyle();
            assertEquals(90, c6Style.getRotation());
        }
    }

    @Test
    void sheet1_numberFormat() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);

            // B3: "0.00"
            CellStyle b3Style = sheet.getRow(2).getCell(1).getCellStyle();
            String b3Fmt = b3Style.getDataFormatString();
            assertEquals("0.00", b3Fmt);

            // D3: "yyyy-MM-dd"
            CellStyle d3Style = sheet.getRow(2).getCell(3).getCellStyle();
            assertEquals("yyyy-MM-dd", d3Style.getDataFormatString());

            // E3: "#,##0.00"
            CellStyle e3Style = sheet.getRow(2).getCell(4).getCellStyle();
            assertEquals("#,##0.00", e3Style.getDataFormatString());

            // B13: "#,##0.00"
            CellStyle b13Style = sheet.getRow(12).getCell(1).getCellStyle();
            assertEquals("#,##0.00", b13Style.getDataFormatString());

            // C13: "0.00%"
            CellStyle c13Style = sheet.getRow(12).getCell(2).getCellStyle();
            assertEquals("0.00%", c13Style.getDataFormatString());
        }
    }

    @Test
    void sheet1_richText() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);

            Cell f3 = sheet.getRow(2).getCell(5);
            assertNotNull(f3);
            assertEquals(CellType.STRING, f3.getCellType());

            XSSFRichTextString rts = (XSSFRichTextString) f3.getRichStringCellValue();
            assertEquals("红色粗体+蓝色斜体", rts.getString());

            // 至少 3 个 formatting run（红色粗体、+、蓝色斜体）
            int runs = rts.numFormattingRuns();
            assertTrue(runs >= 3, "Expected at least 3 formatting runs, got " + runs);

            // 第一段（"红色粗体"）应该是 bold
            Font seg1Font = rts.getFontOfFormattingRun(0);
            assertTrue(seg1Font.getBold(), "First segment should be bold");
        }
    }

    @Test
    void sheet1_allBorderStyles() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);

            // Row 8: thin, hair, dotted, dashed, dashDot, dashDotDot
            assertBorderStyle(sheet, 8, 0, BorderStyle.THIN);
            assertBorderStyle(sheet, 8, 1, BorderStyle.HAIR);
            assertBorderStyle(sheet, 8, 2, BorderStyle.DOTTED);
            assertBorderStyle(sheet, 8, 3, BorderStyle.DASHED);
            assertBorderStyle(sheet, 8, 4, BorderStyle.DASH_DOT);
            assertBorderStyle(sheet, 8, 5, BorderStyle.DASH_DOT_DOT);

            // Row 9: double, medium, mediumDashed, mediumDashDot, mediumDashDotDot, slantDashDot
            assertBorderStyle(sheet, 9, 0, BorderStyle.DOUBLE);
            assertBorderStyle(sheet, 9, 1, BorderStyle.MEDIUM);
            assertBorderStyle(sheet, 9, 2, BorderStyle.MEDIUM_DASHED);
            assertBorderStyle(sheet, 9, 3, BorderStyle.MEDIUM_DASH_DOT);
            assertBorderStyle(sheet, 9, 4, BorderStyle.MEDIUM_DASH_DOT_DOT);
            assertBorderStyle(sheet, 9, 5, BorderStyle.SLANTED_DASH_DOT);

            // Row 10: thick
            assertBorderStyle(sheet, 10, 0, BorderStyle.THICK);
        }
    }

    @Test
    void sheet1_mixedBorders() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);

            // B11: 四边不同线型
            CellStyle style = sheet.getRow(10).getCell(1).getCellStyle();
            assertEquals(BorderStyle.THICK, style.getBorderTop());
            assertEquals(BorderStyle.DASHED, style.getBorderRight());
            assertEquals(BorderStyle.DOTTED, style.getBorderBottom());
            assertEquals(BorderStyle.DOUBLE, style.getBorderLeft());

            // 四边不同颜色
            XSSFCellStyle xssfStyle = (XSSFCellStyle) style;
            assertColorEquals("#FF0000", xssfStyle.getTopBorderXSSFColor());
            assertColorEquals("#0000FF", xssfStyle.getRightBorderXSSFColor());
            assertColorEquals("#00B050", xssfStyle.getBottomBorderXSSFColor());
            assertColorEquals("#7030A0", xssfStyle.getLeftBorderXSSFColor());
        }
    }

    @Test
    void sheet1_borderColors() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);

            // C9: dotted + #FF0000
            XSSFCellStyle c9Style = (XSSFCellStyle) sheet.getRow(8).getCell(2).getCellStyle();
            assertColorEquals("#FF0000", c9Style.getTopBorderXSSFColor());

            // D9: dashed + #00FF00
            XSSFCellStyle d9Style = (XSSFCellStyle) sheet.getRow(8).getCell(3).getCellStyle();
            assertColorEquals("#00FF00", d9Style.getTopBorderXSSFColor());

            // E9: dashDot + #0000FF
            XSSFCellStyle e9Style = (XSSFCellStyle) sheet.getRow(8).getCell(4).getCellStyle();
            assertColorEquals("#0000FF", e9Style.getTopBorderXSSFColor());
        }
    }

    @Test
    void sheet1_padding() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);

            // A3: padding left=14 → indent ≈ 2
            CellStyle a3Style = sheet.getRow(2).getCell(0).getCellStyle();
            assertTrue(a3Style.getIndention() >= 1, "Padding left should produce indent >= 1, got " + a3Style.getIndention());
        }
    }

    // ─── Sheet 2: 尺寸测试 ──────────────────────────────────

    @Test
    void sheet2_nameAndStructure() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(1);
            assertEquals("尺寸测试", sheet.getSheetName());

            // 2 个合并区域
            assertEquals(2, sheet.getNumMergedRegions());

            // 合并 1: A1:B2 (2行×2列)
            CellRangeAddress merge1 = findMerge(sheet, 0, 0);
            assertNotNull(merge1, "Merge at A1 not found");
            assertEquals(0, merge1.getFirstRow());
            assertEquals(1, merge1.getLastRow());
            assertEquals(0, merge1.getFirstColumn());
            assertEquals(1, merge1.getLastColumn());

            // 合并 2: A4:C4 (1行×3列)
            CellRangeAddress merge2 = findMerge(sheet, 3, 0);
            assertNotNull(merge2, "Merge at A4 not found");
            assertEquals(3, merge2.getFirstRow());
            assertEquals(3, merge2.getLastRow());
            assertEquals(0, merge2.getFirstColumn());
            assertEquals(2, merge2.getLastColumn());
        }
    }

    @Test
    void sheet2_customRowHeights() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(1);

            // Row 0: height=60px → 60*0.75=45 points
            Row row0 = sheet.getRow(0);
            assertNotNull(row0);
            assertEquals(45.0f, row0.getHeightInPoints(), 0.5f);

            // Row 1: height=40px → 30 points
            Row row1 = sheet.getRow(1);
            assertNotNull(row1);
            assertEquals(30.0f, row1.getHeightInPoints(), 0.5f);

            // Row 2: height=10px → 7.5 points
            Row row2 = sheet.getRow(2);
            assertNotNull(row2);
            assertEquals(7.5f, row2.getHeightInPoints(), 0.5f);
        }
    }

    @Test
    void sheet2_hiddenRow() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(1);

            // Row 4: hidden
            Row row4 = sheet.getRow(4);
            // hidden row might not exist or has zero height
            if (row4 != null) {
                assertTrue(row4.getZeroHeight(), "Row 4 should be hidden (zero height)");
            }

            // Row 0: not hidden
            Row row0 = sheet.getRow(0);
            assertNotNull(row0);
            assertFalse(row0.getZeroHeight());
        }
    }

    @Test
    void sheet2_customColumnWidths() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(1);

            // Column 0: width=200px → 200*256/7 ≈ 7314
            int w0 = sheet.getColumnWidth(0);
            assertEquals(7314, w0, 200);

            // Column 1: width=60px → 60*256/7 ≈ 2194
            int w1 = sheet.getColumnWidth(1);
            assertEquals(2194, w1, 200);

            // Column 2: width=150px → 150*256/7 ≈ 5485
            int w2 = sheet.getColumnWidth(2);
            assertEquals(5485, w2, 200);
        }
    }

    @Test
    void sheet2_hiddenColumn() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(1);

            // Column 4: hidden
            assertTrue(sheet.isColumnHidden(4), "Column 4 should be hidden");

            // Column 0: not hidden
            assertFalse(sheet.isColumnHidden(0), "Column 0 should not be hidden");
        }
    }

    @Test
    void sheet2_mergedCellStyles() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(1);

            // A1 (master of 2×2 merge): bold, center, #4472C4 fill, medium borders
            Cell a1 = sheet.getRow(0).getCell(0);
            Font a1Font = wb.getFontAt(a1.getCellStyle().getFontIndex());
            assertTrue(a1Font.getBold());
            assertEquals(14, a1Font.getFontHeightInPoints());

            XSSFCellStyle a1Style = (XSSFCellStyle) a1.getCellStyle();
            assertColorEquals("#4472C4", a1Style.getFillForegroundXSSFColor());
            assertEquals(BorderStyle.MEDIUM, a1Style.getBorderTop());

            // A4 (3-col merge): dashDot border, italic
            Cell a4 = sheet.getRow(3).getCell(0);
            Font a4Font = wb.getFontAt(a4.getCellStyle().getFontIndex());
            assertTrue(a4Font.getItalic());

            XSSFCellStyle a4Style = (XSSFCellStyle) a4.getCellStyle();
            assertEquals(BorderStyle.DASH_DOT, a4Style.getBorderTop());
            assertColorEquals("#ED7D31", a4Style.getTopBorderXSSFColor());
        }
    }

    // ─── 输出文件（方便手动验证）──────────────────────────────

    @Test
    void writeOutputFile(@TempDir Path tempDir) throws Exception {
        Path outputPath = tempDir.resolve("test-output.xlsx");
        Files.write(outputPath, xlsxBytes);
        assertTrue(Files.exists(outputPath));
        assertTrue(Files.size(outputPath) > 0);

        // 也输出到 target 目录方便手动打开
        Path targetDir = Path.of("target");
        if (Files.exists(targetDir)) {
            Path targetFile = targetDir.resolve("test-output.xlsx");
            Files.write(targetFile, xlsxBytes);
            System.out.println("📊 Excel 测试文件已生成: " + targetFile.toAbsolutePath());
        }
    }

    // ─── 辅助方法 ─────────────────────────────────────────────

    private static void assertBorderStyle(Sheet sheet, int row, int col, BorderStyle expected) {
        Cell cell = sheet.getRow(row).getCell(col);
        assertNotNull(cell, String.format("Cell at row=%d col=%d is null", row, col));
        CellStyle style = cell.getCellStyle();
        assertEquals(expected, style.getBorderTop(),
                String.format("Border mismatch at row=%d col=%d (%s)", row, col, cell.getStringCellValue()));
        assertEquals(expected, style.getBorderRight());
        assertEquals(expected, style.getBorderBottom());
        assertEquals(expected, style.getBorderLeft());
    }

    private static CellRangeAddress findMerge(Sheet sheet, int row, int col) {
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.getFirstRow() == row && region.getFirstColumn() == col) {
                return region;
            }
        }
        return null;
    }

    private static void assertColorEquals(String expectedHex, XSSFColor actual) {
        assertNotNull(actual, "Color is null, expected " + expectedHex);
        byte[] rgb = actual.getRGB();
        assertNotNull(rgb, "Color RGB is null");
        String actualHex = String.format("#%02X%02X%02X", rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF);
        assertEquals(expectedHex.toUpperCase(), actualHex,
                "Color mismatch: expected " + expectedHex + " but got " + actualHex);
    }

    private static void assertColorEquals(String expectedHex, XSSFFont font) {
        XSSFColor color = font.getXSSFColor();
        assertNotNull(color, "Font color is null, expected " + expectedHex);
        byte[] rgb = color.getRGB();
        assertNotNull(rgb, "Font color RGB is null");
        String actualHex = String.format("#%02X%02X%02X", rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF);
        assertEquals(expectedHex.toUpperCase(), actualHex,
                "Font color mismatch: expected " + expectedHex + " but got " + actualHex);
    }
}
