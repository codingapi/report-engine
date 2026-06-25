package com.codingapi.report.datasource.db;

import static org.junit.jupiter.api.Assertions.*;

import com.codingapi.report.data.dataset.DataType;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.datasource.ColumnMeta;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.DataSourceType;
import com.codingapi.report.data.datasource.RawTable;
import com.codingapi.report.data.datasource.TestResult;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DbDataExtractorTest {

    private static final String URL = "jdbc:h2:mem:dbextractor;DB_CLOSE_DELAY=-1";
    private final DbDataExtractor extractor = new DbDataExtractor();

    private DataSource dbSource() {
        return DataSource.builder()
                .id("ds")
                .name("h2")
                .type(DataSourceType.DB)
                .config(
                        Map.of(
                                "url", URL,
                                "driver", "org.h2.Driver",
                                "username", "sa",
                                "password", ""))
                .build();
    }

    @BeforeAll
    static void setup() throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
                Statement stmt = conn.createStatement()) {
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS TEST_TABLE (ID BIGINT PRIMARY KEY, NAME VARCHAR(100))");
            stmt.execute("MERGE INTO TEST_TABLE VALUES (1, 'alice')");
            stmt.execute("MERGE INTO TEST_TABLE VALUES (2, 'bob')");
        }
    }

    @Test
    void supportsDbOnly() {
        assertTrue(extractor.supports(DataSourceType.DB));
        assertFalse(extractor.supports(DataSourceType.CSV));
    }

    @Test
    void extractReturnsQualifiedRows() {
        TableDataset ds =
                TableDataset.builder()
                        .id("t")
                        .datasourceId("ds")
                        .sourceTable("TEST_TABLE")
                        .alias("测试表")
                        .fields(
                                List.of(
                                        Field.builder()
                                                .name("ID")
                                                .dataType(DataType.NUMBER)
                                                .primaryKey(true)
                                                .build(),
                                        Field.builder()
                                                .name("NAME")
                                                .dataType(DataType.STRING)
                                                .build()))
                        .build();
        RawTable raw = extractor.extract(dbSource(), ds);

        assertEquals(List.of("t.ID", "t.NAME"), raw.getColumns());
        assertEquals(2, raw.getRows().size());
        assertEquals(1.0, raw.getRows().get(0).get("t.ID"));
        assertEquals("alice", raw.getRows().get(0).get("t.NAME"));
    }

    @Test
    void testConnectionSucceeds() {
        TestResult result = extractor.test(dbSource());
        assertTrue(result.ok(), () -> "连接应成功: " + result.message());
    }

    @Test
    void testConnectionFailsOnBadUrl() {
        DataSource bad =
                DataSource.builder()
                        .id("ds")
                        .type(DataSourceType.DB)
                        .config(
                                Map.of(
                                        "url",
                                        "jdbc:h2:mem:nope;IFEXISTS=TRUE",
                                        "driver",
                                        "org.h2.Driver"))
                        .build();
        TestResult result = extractor.test(bad);
        assertFalse(result.ok());
    }

    @Test
    void listTablesIncludesTestTable() {
        List<String> tables = extractor.listTables(dbSource());
        assertTrue(tables.contains("TEST_TABLE"));
    }

    @Test
    void listColumnsMarksPrimaryKey() {
        List<ColumnMeta> cols = extractor.listColumns(dbSource(), "TEST_TABLE");
        ColumnMeta idCol =
                cols.stream().filter(c -> c.name().equals("ID")).findFirst().orElseThrow();
        assertTrue(idCol.primaryKey());
        ColumnMeta nameCol =
                cols.stream().filter(c -> c.name().equals("NAME")).findFirst().orElseThrow();
        assertFalse(nameCol.primaryKey());
    }
}
