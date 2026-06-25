package com.codingapi.report.datasource.json;

import com.codingapi.report.data.dataset.DataType;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.datasource.DataExtractor;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.DataSourceType;
import com.codingapi.report.data.datasource.RawTable;
import com.codingapi.report.data.datasource.TestResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON 数据提取器：读 JSON 文件或字面量字符串，按 {@code dataPath} 取数组，按 {@link Dataset#getFields()} 映射为
 * {@link RawTable}。
 *
 * <h3>设计要点</h3>
 *
 * <ul>
 *   <li>JSON 解析用项目已有的 Jackson {@link ObjectMapper}
 *   <li>{@code config.path} 与 {@code config.content} 二选一：有 {@code path} 读文件，否则按 {@code content} 字面量解析
 *   <li>{@code path} 支持 {@code classpath:xxx} 前缀（从类路径载入）与文件系统路径
 *   <li>{@code dataPath} 支持 {@code $} / {@code $.a.b} / {@code /a/b} 三种写法，统一转为 Jackson JsonPointer
 *   <li>列名用限定名 {@code datasetId.field}（与 {@code DbDataExtractor}/{@code ApiDataExtractor} 一致）
 *   <li>类型归一：{@code NUMBER→Double}、{@code BOOLEAN→Boolean}、其余 → {@code String}
 * </ul>
 */
@Slf4j
public class JsonDataExtractor implements DataExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean supports(DataSourceType type) {
        return type == DataSourceType.JSON;
    }

    @Override
    public RawTable extract(DataSource source, Dataset dataset) {
        JsonConfig config = JsonConfig.from(source);
        log.debug("JSON 提取: {} dataPath={}", dataset.getId(), config.dataPath());
        try {
            JsonNode root = parseRoot(config);
            JsonNode dataNode = selectByPath(root, config.dataPath());
            if (!dataNode.isArray()) {
                throw new IllegalStateException("dataPath 指向的不是数组: " + config.dataPath());
            }
            return readRows((ArrayNode) dataNode, dataset);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 提取失败: " + dataset.getId(), e);
        }
    }

    @Override
    public TestResult test(DataSource source) {
        long start = System.currentTimeMillis();
        try {
            JsonConfig config = JsonConfig.from(source);
            parseRoot(config);
            long latency = System.currentTimeMillis() - start;
            return new TestResult(true, "JSON 解析成功", latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new TestResult(false, "JSON 解析失败: " + e.getMessage(), latency);
        }
    }

    // ============================================================
    // 内部
    // ============================================================

    private JsonNode parseRoot(JsonConfig config) throws Exception {
        if (config.path() != null && !config.path().isBlank()) {
            return MAPPER.readTree(readContent(config.path()));
        }
        return MAPPER.readTree(config.content());
    }

    private String readContent(String path) throws Exception {
        if (path.startsWith("classpath:")) {
            String res = path.substring("classpath:".length());
            try (var in = getClass().getClassLoader().getResourceAsStream(res)) {
                if (in == null) {
                    throw new IllegalStateException("classpath 资源不存在: " + res);
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        Path p = Paths.get(path);
        return Files.readString(p, StandardCharsets.UTF_8);
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
            case BOOLEAN ->
                    value.isBoolean() ? value.booleanValue() : Boolean.parseBoolean(value.asText());
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
