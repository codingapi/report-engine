package com.example.report.repository;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 报表配置仓库（暂用内存 Map 存储，后续替换为持久化落库）。
 * <p>
 * 报表配置以原样 JSON（Map）保存——包含 name/cellBindings/loopBlocks/summaries/params/template，
 * 后端不解析其结构，仅做存取；渲染走独立的 /api/report/render 接口。
 * </p>
 */
@Component
public class ReportRepository {

    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    /** 保存（无 id 则生成），返回报表 id。 */
    public String save(Map<String, Object> config) {
        Object idObj = config.get("id");
        String id = (idObj instanceof String s && !s.isBlank()) ? s : UUID.randomUUID().toString();
        config.put("id", id);
        store.put(id, config);
        return id;
    }

    /** 按 id 加载完整配置，不存在返回 null。 */
    public Map<String, Object> find(String id) {
        return store.get(id);
    }

    /** 列出全部报表的完整配置。 */
    public List<Map<String, Object>> all() {
        return new ArrayList<>(store.values());
    }
}
