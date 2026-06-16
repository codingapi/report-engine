package com.codingapi.report.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ReportRepository} 的默认内存实现（开箱即用）。
 * <p>
 * 报表配置以原样 JSON（Map）保存；进程内存储，重启丢失。使用方可提供持久化实现覆盖。
 */
public class InMemoryReportRepository implements ReportRepository {

    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    @Override
    public String save(Map<String, Object> config) {
        Object idObj = config.get("id");
        String id = (idObj instanceof String s && !s.isBlank()) ? s : UUID.randomUUID().toString();
        config.put("id", id);
        store.put(id, config);
        return id;
    }

    @Override
    public Map<String, Object> find(String id) {
        return store.get(id);
    }

    @Override
    public List<Map<String, Object>> all() {
        return new ArrayList<>(store.values());
    }
}
