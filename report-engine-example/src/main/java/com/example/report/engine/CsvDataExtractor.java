package com.example.report.engine;

import com.example.report.model.source.DataSource;
import com.example.report.model.source.DataSourceType;
import com.example.report.model.source.Dataset;
import com.example.report.model.source.DataType;
import com.example.report.model.source.Field;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV 提取器：从 classpath 资源读取 CSV（路径取自 {@code DataSource.config["path"]}），
 * 按数据集字段名匹配 CSV 表头，并按字段类型归一化值。
 *
 * <p>最小实现：逗号分隔、不处理引号转义；取整表（过滤交给 Java 加工层）。
 */
public class CsvDataExtractor implements DataExtractor {

    @Override
    public boolean supports(DataSourceType type) {
        return type == DataSourceType.CSV;
    }

    @Override
    public RawTable extract(DataSource source, Dataset dataset) {
        String path = (String) source.getConfig().get("path");
        List<String> lines = readLines(path);
        if (lines.isEmpty()) {
            return new RawTable(qualifiedColumns(dataset), new ArrayList<>());
        }

        String[] header = lines.get(0).split(",", -1);
        Map<String, Integer> headerIndex = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            headerIndex.put(header[i].trim(), i);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int li = 1; li < lines.size(); li++) {
            String line = lines.get(li);
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split(",", -1);
            Map<String, Object> row = new LinkedHashMap<>();
            for (Field f : dataset.getFields()) {
                Integer idx = headerIndex.get(f.getName());
                Object val = (idx == null || idx >= parts.length)
                        ? null
                        : coerce(parts[idx].trim(), f.getDataType());
                row.put(dataset.getId() + "." + f.getName(), val);
            }
            rows.add(row);
        }
        return new RawTable(qualifiedColumns(dataset), rows);
    }

    private static List<String> qualifiedColumns(Dataset dataset) {
        List<String> cols = new ArrayList<>();
        for (Field f : dataset.getFields()) {
            cols.add(dataset.getId() + "." + f.getName());
        }
        return cols;
    }

    private static Object coerce(String s, DataType type) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        return switch (type) {
            case NUMBER -> Double.parseDouble(s);
            case BOOLEAN -> Boolean.parseBoolean(s);
            default -> s;
        };
    }

    private List<String> readLines(String path) {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("找不到 CSV 资源: " + path);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        } catch (Exception e) {
            throw new IllegalStateException("读取 CSV 失败: " + path, e);
        }
    }
}
