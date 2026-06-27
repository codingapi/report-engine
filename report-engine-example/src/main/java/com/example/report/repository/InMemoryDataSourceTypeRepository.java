package com.example.report.repository;

import com.codingapi.report.data.datasource.DataSourceTypeConfig;
import com.codingapi.report.repository.DataSourceTypeRepository;
import com.codingapi.report.repository.PageQuery;
import com.codingapi.report.repository.PageResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link DataSourceTypeRepository} 的内存实现（example 演示用）。
 *
 * <p>以领域 {@link DataSourceTypeConfig} 保存；进程内存储，重启丢失。生产环境应由使用方提供持久化实现。
 */
public class InMemoryDataSourceTypeRepository implements DataSourceTypeRepository {

    private final ConcurrentHashMap<String, DataSourceTypeConfig> store = new ConcurrentHashMap<>();

    @Override
    public String save(DataSourceTypeConfig config) {
        String id =
                config.getId() != null && !config.getId().isBlank()
                        ? config.getId()
                        : UUID.randomUUID().toString();
        config.setId(id);

        long now = System.currentTimeMillis();
        DataSourceTypeConfig existing = store.get(id);
        config.setCreateTime(existing != null ? existing.getCreateTime() : now);
        config.setUpdateTime(now);

        store.put(id, config);
        return id;
    }

    @Override
    public DataSourceTypeConfig find(String id) {
        return store.get(id);
    }

    @Override
    public PageResult<DataSourceTypeConfig> page(PageQuery query) {
        int current = query.current();
        int pageSize = query.pageSize();
        List<DataSourceTypeConfig> all = new ArrayList<>(store.values());
        all.sort((a, b) -> Long.compare(b.getCreateTime(), a.getCreateTime()));
        long total = all.size();
        int from = (int) Math.min((long) (current - 1) * pageSize, total);
        int to = (int) Math.min((long) from + pageSize, total);
        return new PageResult<>(all.subList(from, to), total);
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }
}
