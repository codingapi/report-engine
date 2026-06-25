package com.codingapi.report.datasource.json;

import com.codingapi.report.data.datasource.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * JSON 数据源连接配置：从 {@link DataSource#getConfig()} 解析而来。
 *
 * <h3>配置项约定</h3>
 *
 * <ul>
 *   <li>{@code path}（与 {@code content} 二选一）：JSON 文件路径，可指向 classpath 资源（{@code classpath:foo.json}）
 *       或文件系统绝对/相对路径
 *   <li>{@code content}（与 {@code path} 二选一）：JSON 字符串字面量，便于内联测试或运行时拼装
 *   <li>{@code dataPath}：JSON 数组在文档中的位置，默认 {@code $}（根）
 *       <ul>
 *         <li>{@code $} 或空 → 根节点
 *         <li>{@code $.data.items} → Jackson JsonPointer {@code /data/items}
 *         <li>{@code /data/items} → 原样当 JsonPointer
 *       </ul>
 * </ul>
 *
 * <p>{@code path} 与 {@code content} 至少一个有值，否则抛 {@link IllegalStateException}。
 */
public record JsonConfig(String path, String content, String dataPath) {

    public static JsonConfig from(DataSource source) {
        Map<String, Object> raw = source.getConfig() != null ? source.getConfig() : new HashMap<>();
        String path = str(raw.get("path"));
        String content = str(raw.get("content"));
        if ((path == null || path.isBlank()) && (content == null || content.isBlank())) {
            throw new IllegalStateException("JSON 数据源必须配置 config.path 或 config.content");
        }
        String dataPath = strOr(raw.get("dataPath"), "$");
        return new JsonConfig(path, content, dataPath);
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String strOr(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v);
        return s.isBlank() ? def : s;
    }
}
