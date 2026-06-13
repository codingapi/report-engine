package com.codingapi.report.engine;

import com.codingapi.report.model.source.DataSource;
import com.codingapi.report.model.source.DataSourceType;
import com.codingapi.report.model.source.Dataset;
import com.codingapi.report.model.source.DataType;
import com.codingapi.report.model.source.Field;

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
 * CSV 数据提取器：从 classpath 资源读取 CSV 文件，转为 {@link RawTable}。
 *
 * <h3>数据源配置</h3>
 * <p>CSV 文件路径通过 {@code DataSource.config["path"]} 指定，如：
 * <pre>
 *   DataSource.builder()
 *     .id("csv_employees")
 *     .type(DataSourceType.CSV)
 *     .config(Map.of("path", "/data/employees.csv"))  // classpath 路径
 *     .build()
 * </pre>
 *
 * <h3>提取过程</h3>
 * <ol>
 *   <li>读取 CSV 全部行（第一行为表头）</li>
 *   <li>用 Dataset 的字段名列表匹配 CSV 表头，建立"字段名 → 列索引"映射</li>
 *   <li>逐行提取，按字段的 {@link com.codingapi.report.model.source.DataType} 归一化值：
 *       NUMBER → Double，BOOLEAN → Boolean，其余 → String</li>
 *   <li>列名使用限定名 {@code datasetId.field}（由 {@link RawTable} 约定），避免多表 join 后字段冲突</li>
 * </ol>
 *
 * <h3>当前实现的限制（最小引擎）</h3>
 * <ul>
 *   <li>逗号分隔，不处理引号转义（字段值不能包含逗号）</li>
 *   <li>取整表，不做过滤下推（过滤交给 {@link Operators} 在 Java 层完成）</li>
 *   <li>无编码检测，固定 UTF-8</li>
 * </ul>
 *
 * <p>这些限制对测试和演示场景足够，生产环境可扩展为完整的 CSV 解析（如引入 OpenCSV）。
 */
public class CsvDataExtractor implements DataExtractor {

    @Override
    public boolean supports(DataSourceType type) {
        return type == DataSourceType.CSV;
    }

    @Override
    public RawTable extract(DataSource source, Dataset dataset) {
        // 1. 读取 CSV 全部行
        String path = (String) source.getConfig().get("path");
        List<String> lines = readLines(path);
        if (lines.isEmpty()) {
            return new RawTable(qualifiedColumns(dataset), new ArrayList<>());
        }

        // 2. 解析表头：建立"CSV 列名 → 列索引"映射，用于后续按字段名查找
        String[] header = lines.get(0).split(",", -1);
        Map<String, Integer> headerIndex = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            headerIndex.put(header[i].trim(), i);
        }

        // 3. 逐行提取：按 Dataset 的字段定义选取列、归一化类型、使用限定列名
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
                // 字段名在 CSV 表头中找不到，或列数不够 → null
                Object val = (idx == null || idx >= parts.length)
                        ? null
                        : coerce(parts[idx].trim(), f.getDataType());
                // 限定列名：datasetId.field（join 后不会与其他表的同名字段冲突）
                row.put(dataset.getId() + "." + f.getName(), val);
            }
            rows.add(row);
        }
        return new RawTable(qualifiedColumns(dataset), rows);
    }

    /** 生成限定列名列表：每个字段 → "datasetId.field" */
    private static List<String> qualifiedColumns(Dataset dataset) {
        List<String> cols = new ArrayList<>();
        for (Field f : dataset.getFields()) {
            cols.add(dataset.getId() + "." + f.getName());
        }
        return cols;
    }

    /**
     * 类型归一化：将 CSV 字符串值按字段的 DataType 转换为 Java 类型。
     * <ul>
     *   <li>NUMBER → {@code Double}（使 Operators 的数值比较和聚合能正确工作）</li>
     *   <li>BOOLEAN → {@code Boolean}</li>
     *   <li>其余（STRING/DATE/DATETIME/JSON）→ 保持 {@code String}</li>
     * </ul>
     * 空字符串或 null → 返回 null。
     */
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
