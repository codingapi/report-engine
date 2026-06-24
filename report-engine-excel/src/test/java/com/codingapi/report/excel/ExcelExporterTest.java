package com.codingapi.report.excel;

import static org.junit.jupiter.api.Assertions.*;

import com.codingapi.report.excel.pojo.Workbook;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExcelExporterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static Workbook workbookDTO;
    private static byte[] xlsxBytes;

    @BeforeAll
    static void setUp() throws Exception {
        InputStream is = ExcelExporterTest.class.getResourceAsStream("/test-workbook.json");
        assertNotNull(is);

        workbookDTO = MAPPER.readValue(is, Workbook.class);
        assertNotNull(workbookDTO.getSheets());
        assertEquals(2, workbookDTO.getSheets().size());

        ExcelExporter exporter = new ExcelExporter();
        xlsxBytes = exporter.export(workbookDTO);
        assertNotNull(xlsxBytes);
        assertTrue(xlsxBytes.length > 0);
    }

    @Test
    void generatedFileCanBeOpenedByPoi() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            assertEquals(2, wb.getNumberOfSheets());
        }
    }

    @Test
    void sheet1_nameAndStructure() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("样式测试", sheet.getSheetName());
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
            assertEquals("报表样式验证", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Hello World", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals(3.14159, sheet.getRow(2).getCell(1).getNumericCellValue(), 0.00001);
            assertTrue(sheet.getRow(2).getCell(2).getBooleanCellValue());
        }
    }

    @Test
    void sheet1_formula() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);
            Cell formulaCell = sheet.getRow(2).getCell(4);
            assertEquals(CellType.FORMULA, formulaCell.getCellType());
            assertEquals("SUM(B3:B3)", formulaCell.getCellFormula());
        }
    }

    @Test
    void sheet1_fontStyles() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);

            Font titleFont = wb.getFontAt(sheet.getRow(0).getCell(0).getCellStyle().getFontIndex());
            assertTrue(titleFont.getBold());
            assertEquals(18, titleFont.getFontHeightInPoints());

            Font a3Font = wb.getFontAt(sheet.getRow(2).getCell(0).getCellStyle().getFontIndex());
            assertTrue(a3Font.getItalic());
            assertEquals("Arial", a3Font.getFontName());

            Font a8Font = wb.getFontAt(sheet.getRow(7).getCell(0).getCellStyle().getFontIndex());
            assertTrue(a8Font.getUnderline() != 0);

            Font b8Font = wb.getFontAt(sheet.getRow(7).getCell(1).getCellStyle().getFontIndex());
            assertTrue(b8Font.getStrikeout());
        }
    }

    @Test
    void sheet1_backgroundFill() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);
            XSSFCellStyle titleStyle = (XSSFCellStyle) sheet.getRow(0).getCell(0).getCellStyle();
            assertColorEquals("#1F4E79", titleStyle.getFillForegroundXSSFColor());
            assertEquals(FillPatternType.SOLID_FOREGROUND, titleStyle.getFillPattern());
        }
    }

    @Test
    void sheet1_alignment() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals(
                    HorizontalAlignment.LEFT,
                    sheet.getRow(4).getCell(0).getCellStyle().getAlignment());
            assertEquals(
                    VerticalAlignment.TOP,
                    sheet.getRow(4).getCell(0).getCellStyle().getVerticalAlignment());
            assertEquals(
                    HorizontalAlignment.CENTER,
                    sheet.getRow(4).getCell(1).getCellStyle().getAlignment());
            assertEquals(
                    HorizontalAlignment.RIGHT,
                    sheet.getRow(4).getCell(2).getCellStyle().getAlignment());
            assertEquals(
                    VerticalAlignment.BOTTOM,
                    sheet.getRow(4).getCell(2).getCellStyle().getVerticalAlignment());
        }
    }

    @Test
    void sheet1_wrapAndRotation() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertTrue(sheet.getRow(5).getCell(0).getCellStyle().getWrapText());
            assertEquals(45, sheet.getRow(5).getCell(1).getCellStyle().getRotation());
            assertEquals(90, sheet.getRow(5).getCell(2).getCellStyle().getRotation());
        }
    }

    @Test
    void sheet1_allBorderStyles() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertBorderStyle(sheet, 8, 0, BorderStyle.THIN);
            assertBorderStyle(sheet, 8, 1, BorderStyle.HAIR);
            assertBorderStyle(sheet, 8, 2, BorderStyle.DOTTED);
            assertBorderStyle(sheet, 8, 3, BorderStyle.DASHED);
            assertBorderStyle(sheet, 8, 4, BorderStyle.DASH_DOT);
            assertBorderStyle(sheet, 8, 5, BorderStyle.DASH_DOT_DOT);
            assertBorderStyle(sheet, 9, 0, BorderStyle.DOUBLE);
            assertBorderStyle(sheet, 9, 1, BorderStyle.MEDIUM);
            assertBorderStyle(sheet, 9, 2, BorderStyle.MEDIUM_DASHED);
            assertBorderStyle(sheet, 9, 3, BorderStyle.MEDIUM_DASH_DOT);
            assertBorderStyle(sheet, 9, 4, BorderStyle.MEDIUM_DASH_DOT_DOT);
            assertBorderStyle(sheet, 9, 5, BorderStyle.SLANTED_DASH_DOT);
            assertBorderStyle(sheet, 10, 0, BorderStyle.THICK);
        }
    }

    @Test
    void sheet1_richText() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(0);
            Cell f3 = sheet.getRow(2).getCell(5);
            XSSFRichTextString rts = (XSSFRichTextString) f3.getRichStringCellValue();
            assertEquals("红色粗体+蓝色斜体", rts.getString());
            assertTrue(rts.numFormattingRuns() >= 3);
            Font seg1Font = rts.getFontOfFormattingRun(0);
            assertTrue(seg1Font.getBold());
        }
    }

    @Test
    void sheet2_customDimensions() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheetAt(1);
            assertEquals("尺寸测试", sheet.getSheetName());
            assertEquals(2, sheet.getNumMergedRegions());

            Row row0 = sheet.getRow(0);
            assertEquals(45.0f, row0.getHeightInPoints(), 0.5f);

            assertTrue(sheet.isColumnHidden(4));
            assertFalse(sheet.isColumnHidden(0));
        }
    }

    @Test
    void writeOutputFile(@TempDir Path tempDir) throws Exception {
        Path outputPath = tempDir.resolve("test-output.xlsx");
        Files.write(outputPath, xlsxBytes);
        assertTrue(Files.exists(outputPath));
        assertTrue(Files.size(outputPath) > 0);

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
        assertNotNull(cell);
        CellStyle style = cell.getCellStyle();
        assertEquals(expected, style.getBorderTop());
        assertEquals(expected, style.getBorderRight());
        assertEquals(expected, style.getBorderBottom());
        assertEquals(expected, style.getBorderLeft());
    }

    private static void assertColorEquals(
            String expectedHex, org.apache.poi.xssf.usermodel.XSSFColor actual) {
        assertNotNull(actual);
        byte[] rgb = actual.getRGB();
        assertNotNull(rgb);
        String actualHex =
                String.format("#%02X%02X%02X", rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF);
        assertEquals(expectedHex.toUpperCase(), actualHex);
    }
}
