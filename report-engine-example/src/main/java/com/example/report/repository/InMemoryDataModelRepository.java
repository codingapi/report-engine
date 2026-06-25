package com.example.report.repository;

import com.codingapi.report.config.DataModelConfig;
import com.codingapi.report.repository.DataModelRepository;
import com.codingapi.report.repository.PageQuery;
import com.codingapi.report.repository.PageResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link DataModelRepository} 的内存实现（example 演示用）。
 *
 * <p>数据模型配置以强类型 {@link DataModelConfig} 实体保存；进程内存储，重启丢失。 生产环境应由使用方提供持久化实现。 范式同 {@link
 * InMemoryReportRepository}。
 */
public class InMemoryDataModelRepository implements DataModelRepository {

    private final Map<String, DataModelConfig> store = new ConcurrentHashMap<>();

    @Override
    public String save(DataModelConfig config) {
        Object idObj = config.getId();
        String id = (idObj instanceof String s && !s.isBlank()) ? s : UUID.randomUUID().toString();
        config.setId(id);

        long now = System.currentTimeMillis();
        DataModelConfig existing = store.get(id);
        config.setCreateTime(existing != null ? existing.getCreateTime() : now);
        config.setUpdateTime(now);

        // null 列表归一为空，避免前端拿到 null
        if (config.getDatasources() == null) config.setDatasources(List.of());
        if (config.getDatasets() == null) config.setDatasets(List.of());
        if (config.getRelationships() == null) config.setRelationships(List.of());

        store.put(id, config);
        return id;
    }

    @Override
    public DataModelConfig find(String id) {
        return store.get(id);
    }

    @Override
    public PageResult<DataModelConfig> page(PageQuery query) {
        int current = query.current();
        int pageSize = query.pageSize();
        List<DataModelConfig> all = new ArrayList<>(store.values());
        // 按创建时间倒序排列
        all.sort((a, b) -> Long.compare(b.getCreateTime(), a.getCreateTime()));
        long total = all.size();
        int from = (int) Math.min((long) (current - 1) * pageSize, total);
        int to = (int) Math.min((long) from + pageSize, total);
        List<DataModelConfig> pageList = all.subList(from, to);
        return new PageResult<>(pageList, total);
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }
}
