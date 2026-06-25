package com.codingapi.report.datasource.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingapi.report.data.dataset.DataType;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.DataSourceType;
import com.codingapi.report.data.datasource.RawTable;
import com.codingapi.report.data.datasource.TestResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 用 JDK 内置 {@link com.sun.net.httpserver.HttpServer} 起一个最小 HTTP 服务做测试， 不引入 MockWebServer 等额外依赖。
 */
class ApiDataExtractorTest {

    private static HttpServer server;
    private static int port;
    private static final ApiDataExtractor extractor = new ApiDataExtractor();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/items", new StringHandler(
                "{\"data\":[{\"id\":1,\"name\":\"alice\",\"price\":10.5,\"active\":true},"
                        + "{\"id\":2,\"name\":\"bob\",\"price\":20,\"active\":false}]}"));
        server.createContext("/flat", new StringHandler(
                "[{\"id\":1,\"name\":\"alice\"},{\"id\":2,\"name\":\"bob\"}]"));
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private DataSource apiSource(String path, String dataPath) {
        return DataSource.builder()
                .id("api")
                .name("test-api")
                .type(DataSourceType.API)
                .config(Map.of(
                        "url", "http://localhost:" + port + path,
                        "dataPath", dataPath,
                        "timeoutMs", 5000))
                .build();
    }

    private TableDataset dataset() {
        return TableDataset.builder()
                .id("items")
                .datasourceId("api")
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
    void supports_onlyApi() {
        assertTrue(extractor.supports(DataSourceType.API));
        assertFalse(extractor.supports(DataSourceType.DB));
        assertFalse(extractor.supports(DataSourceType.CSV));
    }

    @Test
    void extract_nestedDataPath() {
        RawTable table = extractor.extract(apiSource("/items", "$.data"), dataset());
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
    void extract_rootDataPath() {
        RawTable table = extractor.extract(apiSource("/flat", "$"), dataset());
        assertEquals(2, table.getRows().size());
        assertEquals("alice", table.getRows().get(0).get("items.name"));
        // flat 端点没有 price/active，对应字段为 null
        assertFalse(table.getRows().get(0).containsKey("items.price")
                && table.getRows().get(0).get("items.price") != null);
    }

    @Test
    void extract_pointerStylePath() {
        // /data 风格的 JsonPointer
        RawTable table = extractor.extract(apiSource("/items", "/data"), dataset());
        assertEquals(2, table.getRows().size());
        assertEquals("bob", table.getRows().get(1).get("items.name"));
    }

    @Test
    void test_ok() {
        TestResult result = extractor.test(apiSource("/items", "$.data"));
        assertTrue(result.ok(), result.message());
    }

    @Test
    void test_fail() {
        DataSource bad = DataSource.builder()
                .id("api")
                .name("bad")
                .type(DataSourceType.API)
                .config(Map.of("url", "http://localhost:1/nope", "timeoutMs", 500))
                .build();
        TestResult result = extractor.test(bad);
        assertFalse(result.ok());
    }

    private static class StringHandler implements HttpHandler {
        private final byte[] body;

        StringHandler(String body) {
            this.body = body.getBytes();
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        }
    }
}
