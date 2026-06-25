package com.codingapi.report.datasource.api;

import com.codingapi.report.data.dataset.DataType;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.datasource.DataExtractor;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.DataSourceType;
import com.codingapi.report.data.datasource.RawTable;
import com.codingapi.report.data.datasource.TestResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * HTTP API 数据提取器：发请求拿 JSON，按 {@code dataPath} 取数组，按 {@link Dataset#getFields()} 映射为 {@link RawTable}。
 *
 * <h3>设计要点</h3>
 *
 * <ul>
 *   <li>使用 JDK 内置 {@link java.net.http.HttpClient}，不引入第三方 HTTP 库
 *   <li>JSON 解析用项目已有的 Jackson {@link ObjectMapper}
 *   <li>{@code dataPath} 支持 {@code $} / {@code $.a.b} / {@code /a/b} 三种写法，统一转为 Jackson JsonPointer
 *   <li>列名用限定名 {@code datasetId.field}（与 {@code DbDataExtractor} 一致）
 *   <li>类型归一：{@code NUMBER→Double}、{@code BOOLEAN→Boolean}、其余 → {@code String}
 *   <li>{@link TableDataset#getSourceTable()} 在 API 场景当作 endpoint path，拼到 {@code url} 后面
 * </ul>
 */
@Slf4j
public class ApiDataExtractor implements DataExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean supports(DataSourceType type) {
        return type == DataSourceType.API;
    }

    @Override
    public RawTable extract(DataSource source, Dataset dataset) {
        ApiConfig config = ApiConfig.from(source);
        String url = resolveUrl(config, dataset);
        log.debug("API 提取: {} url={}", dataset.getId(), url);
        try {
            JsonNode root = sendRequest(config, url);
            JsonNode dataNode = selectByPath(root, config.dataPath());
            if (!dataNode.isArray()) {
                throw new IllegalStateException("dataPath 指向的不是数组: " + config.dataPath());
            }
            return readRows((ArrayNode) dataNode, dataset);
        } catch (Exception e) {
            throw new IllegalStateException("API 提取失败: " + dataset.getId(), e);
        }
    }

    @Override
    public TestResult test(DataSource source) {
        long start = System.currentTimeMillis();
        try {
            ApiConfig config = ApiConfig.from(source);
            sendRequest(config, config.url());
            long latency = System.currentTimeMillis() - start;
            return new TestResult(true, "连接成功", latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new TestResult(false, "连接失败: " + e.getMessage(), latency);
        }
    }

    // ============================================================
    // 内部
    // ============================================================

    private String resolveUrl(ApiConfig config, Dataset dataset) {
        if (dataset instanceof TableDataset t) {
            String path = t.getSourceTable();
            if (path != null && !path.isBlank()) {
                String base = config.url();
                if (base.endsWith("/")) {
                    return base + path;
                }
                return base + "/" + path;
            }
        }
        return config.url();
    }

    private JsonNode sendRequest(ApiConfig config, String url) throws Exception {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofMillis(config.timeoutMs()));
        String method = config.method().toUpperCase();
        if ("GET".equals(method)) {
            builder.GET();
        } else if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            HttpRequest.BodyPublisher body =
                    config.body() == null || config.body().isEmpty()
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(config.body());
            builder.method(method, body);
        } else if ("DELETE".equals(method)) {
            builder.DELETE();
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        if (config.headers() != null) {
            config.headers().forEach(builder::header);
        }
        HttpClient client =
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(config.timeoutMs())).build();
        HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("HTTP 状态码 " + resp.statusCode());
        }
        return MAPPER.readTree(resp.body());
    }

    private RawTable readRows(ArrayNode dataNode, Dataset dataset) {
        List<String> columns = new ArrayList<>();
        for (Field f : dataset.getFields()) {
            columns.add(dataset.getId() + "." + f.getName());
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode row : dataNode) {
            Map<String, Object> r = new LinkedHashMap<>();
            for (Field f : dataset.getFields()) {
                JsonNode cell = row.get(f.getName());
                r.put(dataset.getId() + "." + f.getName(), coerce(cell, f.getDataType()));
            }
            rows.add(r);
        }
        return new RawTable(columns, rows);
    }

    private static Object coerce(JsonNode value, DataType type) {
        if (value == null || value.isNull()) return null;
        return switch (type) {
            case NUMBER -> value.isNumber() ? value.doubleValue() : Double.parseDouble(value.asText());
            case BOOLEAN -> value.isBoolean() ? value.booleanValue() : Boolean.parseBoolean(value.asText());
            default -> value.isTextual() ? value.asText() : value.toString();
        };
    }

    private static JsonNode selectByPath(JsonNode root, String dataPath) {
        if (dataPath == null || dataPath.isBlank() || "$".equals(dataPath)) {
            return root;
        }
        return root.at(toPointer(dataPath));
    }

    /** 把 {@code $.a.b} / {@code a.b} / {@code /a/b} 都转成 Jackson JsonPointer 字符串。 */
    private static String toPointer(String dataPath) {
        if (dataPath.startsWith("/")) {
            return dataPath;
        }
        String p = dataPath;
        if (p.startsWith("$.")) {
            p = p.substring(2);
        } else if (p.startsWith("$")) {
            p = p.substring(1);
        }
        if (p.startsWith(".")) {
            p = p.substring(1);
        }
        StringBuilder sb = new StringBuilder();
        for (String seg : p.split("\\.")) {
            if (!seg.isEmpty()) {
                sb.append("/").append(seg.replace("~", "~0").replace("/", "~1"));
            }
        }
        return sb.toString();
    }
}
