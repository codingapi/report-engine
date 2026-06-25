package com.codingapi.report.datasource.json;

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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonDataExtractorTest {

    private static final JsonDataExtractor extractor = new JsonDataExtractor();

    private TableDataset dataset() {
        return TableDataset.builder()
                .id("items")
                .datasourceId("json")
                .sourceTable(null)
                .alias("items")
                .fields(List.of(
                        Field.builder().name("id").dataType(DataType.NUMBER).build(),
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("price").dataType(DataType.NUMBER).build(),
                        Field.builder().name("active").dataType(DataType.BOOLEAN).build()))
                .build();
    }

    private DataSource contentSource(String content, String dataPath) {
        return DataSource.builder()
                .id("json")
                .name("test-json")
                .type(DataSourceType.JSON)
                .config(dataPath == null
                        ? Map.of("content", content)
                        : Map.of("content", content, "dataPath", dataPath))
                .build();
    }

    @Test
    void supports_onlyJson() {
        assertTrue(extractor.supports(DataSourceType.JSON));
        assertFalse(extractor.supports(DataSourceType.DB));
        assertFalse(extractor.supports(DataSourceType.API));
        assertFalse(extractor.supports(DataSourceType.CSV));
    }

    @Test
    void extract_fromContentRootArray() {
        String json = "[{\"id\":1,\"name\":\"alice\",\"price\":10.5,\"active\":true},"
                + "{\"id\":2,\"name\":\"bob\",\"price\":20,\"active\":false}]";
        RawTable table = extractor.extract(contentSource(json, "$"), dataset());
        assertEquals(
                List.of("items.id", "items.name", "items.price", "items.active"),
                table.getColumns());
        assertEquals(2, table.getRows().size());
        Map<String, Object> first = table.getRows().get(0);
        assertEquals(1.0, first.get("items.id"));
        assertEquals("alice", first.get("items.name"));
        assertEquals(10.5, first.get("items.price"));
        assertEquals(Boolean.TRUE, first.get("items.active"));
    }

    @Test
    void extract_fromFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("data.json");
        String json = "[{\"id\":1,\"name\":\"alice\",\"price\":10.5,\"active\":true}]";
        Files.writeString(file, json, StandardCharsets.UTF_8);
        DataSource source = DataSource.builder()
                .id("json")
                .name("file-json")
                .type(DataSourceType.JSON)
                .config(Map.of("path", file.toString()))
                .build();
        RawTable table = extractor.extract(source, dataset());
        assertEquals(1, table.getRows().size());
        assertEquals("alice", table.getRows().get(0).get("items.name"));
    }

    @Test
    void extract_nestedDataPath() {
        String json = "{\"data\":{\"items\":["
                + "{\"id\":1,\"name\":\"alice\",\"price\":10.5,\"active\":true},"
                + "{\"id\":2,\"name\":\"bob\",\"price\":20,\"active\":false}]}}";
        RawTable table = extractor.extract(contentSource(json, "$.data.items"), dataset());
        assertEquals(2, table.getRows().size());
        assertEquals("bob", table.getRows().get(1).get("items.name"));
        assertEquals(20.0, table.getRows().get(1).get("items.price"));
    }

    @Test
    void extract_pointerStylePath() {
        String json = "{\"data\":[{\"id\":1,\"name\":\"alice\",\"price\":1,\"active\":true}]}";
        RawTable table = extractor.extract(contentSource(json, "/data"), dataset());
        assertEquals(1, table.getRows().size());
        assertEquals("alice", table.getRows().get(0).get("items.name"));
    }

    @Test
    void extract_missingFieldIsNullOrCoerced() {
        // JSON 中没有 price/active 字段 → null
        String json = "[{\"id\":1,\"name\":\"alice\"}]";
        RawTable table = extractor.extract(contentSource(json, "$"), dataset());
        assertEquals(1, table.getRows().size());
        assertNull(table.getRows().get(0).get("items.price"));
        assertNull(table.getRows().get(0).get("items.active"));
    }

    @Test
    void test_ok() {
        String json = "[{\"id\":1,\"name\":\"alice\"}]";
        TestResult result = extractor.test(contentSource(json, "$"));
        assertTrue(result.ok(), result.message());
    }

    @Test
    void test_fail() {
        DataSource bad = DataSource.builder()
                .id("json")
                .name("bad")
                .type(DataSourceType.JSON)
                .config(Map.of("content", "{not valid json"))
                .build();
        TestResult result = extractor.test(bad);
        assertFalse(result.ok());
    }
}
