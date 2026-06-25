package com.codingapi.report.datasource.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingapi.report.data.dataset.DataType;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.DataSourceType;
import com.codingapi.report.data.datasource.RawTable;
import com.codingapi.report.data.datasource.TestResult;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 用 POI 在测试启动时动态生成一个小 .xlsx 文件做测试，不引入二进制 test resource。
 *
 * <p>测试覆盖：{@code supports()}、{@code extract()} 正确解析（含表头映射/类型归一/空值）、 {@code
 * test()} 成功与失败路径。
 */
class ExcelDataExtractorTest {

    private static final ExcelDataExtractor extractor = new ExcelDataExtractor();

    @TempDir
    static Path tempDir;

    private static String xlsxPath;

    @BeforeAll
    static void writeXlsx() throws IOException {
        xlsxPath = tempDir.resolve("sample.xlsx").toString();
        try (XSSFWorkbook wb = new XSSFWorkbook();
                FileOutputStream out = new FileOutputStream(xlsxPath)) {
            XSSFSheet sheet = wb.createSheet("data");
            // 表头
            XSSFRow header = sheet.createRow(0);
            header.createCell(0).setCellValue("id");
            header.createCell(1).setCellValue("name");
            header.createCell(2).setCellValue("price");
            header.createCell(3).setCellValue("active");
            // 数据行 1
            XSSFRow r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue(1);
            r1.createCell(1).setCellValue("alice");
            r1.createCell(2).setCellValue(10.5);
            r1.createCell(3).setCellValue(true);
            // 数据行 2
            XSSFRow r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue(2);
            r2.createCell(1).setCellValue("bob");
            r2.createCell(2).setCellValue(20.0);
            r2.createCell(3).setCellValue(false);
            wb.write(out);
        }
    }

    @AfterAll
    static void cleanup() {
        // @TempDir 自动清理
    }

    private DataSource excelSource() {
        return DataSource.builder()
                .id("excel")
                .name("test-excel")
                .type(DataSourceType.EXCEL)
                .config(Map.of("path", xlsxPath, "sheetIndex", 0, "headerRow", 0))
                .build();
    }

    private TableDataset dataset() {
        return TableDataset.builder()
                .id("items")
                .datasourceId("excel")
                .sourceTable(null)
                .alias("items")
                .fields(List.of(
                        Field.builder().name("id").dataType(DataType.NUMBER).build(),
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("price").dataType(DataType.NUMBER).build(),
                        Field.builder().name("active").dataType(DataType.BOOLEAN).build()))
                .build();
    }

    @Test
    void supports_onlyExcel() {
        assertTrue(extractor.supports(DataSourceType.EXCEL));
        assertFalse(extractor.supports(DataSourceType.DB));
        assertFalse(extractor.supports(DataSourceType.CSV));
        assertFalse(extractor.supports(DataSourceType.API));
    }

    @Test
    void extract_parsesRowsAndCoercesTypes() {
        RawTable table = extractor.extract(excelSource(), dataset());
        assertEquals(
                List.of("items.id", "items.name", "items.price", "items.active"),
                table.getColumns());
        assertEquals(2, table.getRows().size());

        Map<String, Object> first = table.getRows().get(0);
        assertEquals(1.0, first.get("items.id"));
        assertEquals("alice", first.get("items.name"));
        assertEquals(10.5, first.get("items.price"));
        assertEquals(Boolean.TRUE, first.get("items.active"));

        Map<String, Object> second = table.getRows().get(1);
        assertEquals(2.0, second.get("items.id"));
        assertEquals("bob", second.get("items.name"));
        assertEquals(20.0, second.get("items.price"));
        assertEquals(Boolean.FALSE, second.get("items.active"));
    }

    @Test
    void extract_missingColumnLeavesNull() {
        // 表里没有 "active" 列时，对应字段应为 null（行初始化时已置 null）
        DataSource source = DataSource.builder()
                .id("excel")
                .name("test-excel")
                .type(DataSourceType.EXCEL)
                .config(Map.of("path", xlsxPath, "sheetIndex", 0, "headerRow", 0))
                .build();
        TableDataset ds = TableDataset.builder()
                .id("items")
                .datasourceId("excel")
                .sourceTable(null)
                .alias("items")
                .fields(List.of(
                        Field.builder().name("id").dataType(DataType.NUMBER).build(),
                        Field.builder().name("missing").dataType(DataType.STRING).build()))
                .build();
        RawTable table = extractor.extract(source, ds);
        assertEquals(List.of("items.id", "items.missing"), table.getColumns());
        assertEquals(2, table.getRows().size());
        assertEquals(1.0, table.getRows().get(0).get("items.id"));
        assertNull(table.getRows().get(0).get("items.missing"));
    }

    @Test
    void test_ok() {
        TestResult result = extractor.test(excelSource());
        assertTrue(result.ok(), result.message());
    }

    @Test
    void test_fail_missingFile() {
        DataSource bad = DataSource.builder()
                .id("excel")
                .name("bad")
                .type(DataSourceType.EXCEL)
                .config(Map.of("path", "/no/such/file.xlsx"))
                .build();
        TestResult result = extractor.test(bad);
        assertFalse(result.ok());
    }

    @Test
    void test_fail_missingPath() {
        DataSource bad = DataSource.builder()
                .id("excel")
                .name("bad")
                .type(DataSourceType.EXCEL)
                .config(Map.of())
                .build();
        TestResult result = extractor.test(bad);
        assertFalse(result.ok());
    }
}
