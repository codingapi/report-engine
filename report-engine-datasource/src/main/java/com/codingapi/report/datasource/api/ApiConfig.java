package com.codingapi.report.datasource.api;

import com.codingapi.report.data.datasource.DataSource;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API 数据源连接配置：从 {@link DataSource#getConfig()} 解析而来。
 *
 * <h3>配置项约定</h3>
 *
 * <ul>
 *   <li>{@code url}（必填）：完整请求 URL
 *   <li>{@code method}：默认 {@code GET}
 *   <li>{@code headers}：{@code Map<String,String>}，请求头
 *   <li>{@code body}：POST/PUT 请求体
 *   <li>{@code dataPath}：JSON 数组在响应中的位置，默认 {@code $}（根）
 *       <ul>
 *         <li>{@code $} 或空 → 根节点
 *         <li>{@code $.data.items} → Jackson JsonPointer {@code /data/items}
 *         <li>{@code /data/items} → 原样当 JsonPointer
 *       </ul>
 *   <li>{@code timeoutMs}：连接 + 读取超时，默认 15000
 * </ul>
 */
public record ApiConfig(
        String url,
        String method,
        Map<String, String> headers,
        String body,
        String dataPath,
        int timeoutMs) {

    private static final int DEFAULT_TIMEOUT_MS = 15000;

    public static ApiConfig from(DataSource source) {
        Map<String, Object> raw = source.getConfig() != null ? source.getConfig() : new HashMap<>();
        String url = str(raw.get("url"));
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("API 数据源缺少 config.url");
        }
        String method = strOr(raw.get("method"), "GET");
        Map<String, String> headers = new LinkedHashMap<>();
        Object h = raw.get("headers");
        if (h instanceof Map<?, ?> hm) {
            for (Map.Entry<?, ?> e : hm.entrySet()) {
                headers.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        }
        String body = str(raw.get("body"));
        String dataPath = strOr(raw.get("dataPath"), "$");
        int timeout = intOr(raw.get("timeoutMs"), DEFAULT_TIMEOUT_MS);
        return new ApiConfig(url, method, headers, body, dataPath, timeout);
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String strOr(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v);
        return s.isBlank() ? def : s;
    }

    private static int intOr(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
