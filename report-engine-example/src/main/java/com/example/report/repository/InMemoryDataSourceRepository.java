package com.example.report.repository;

import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.repository.DataSourceRepository;
import com.codingapi.report.repository.PageQuery;
import com.codingapi.report.repository.PageResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link DataSourceRepository} 的内存实现（example 演示用）。
 *
 * <p>以领域 {@link DataSource} 保存；进程内存储，重启丢失。生产环境应由使用方提供持久化实现。
 */
public class InMemoryDataSourceRepository implements DataSourceRepository {

    private final ConcurrentHashMap<String, DataSource> store = new ConcurrentHashMap<>();

    @Override
    public String save(DataSource source) {
        String id =
                source.getId() != null && !source.getId().isBlank()
                        ? source.getId()
                        : UUID.randomUUID().toString();
        source.setId(id);

        long now = System.currentTimeMillis();
        DataSource existing = store.get(id);
        source.setCreateTime(existing != null ? existing.getCreateTime() : now);
        source.setUpdateTime(now);

        store.put(id, source);
        return id;
    }

    @Override
    public DataSource find(String id) {
        return store.get(id);
    }

    @Override
    public PageResult<DataSource> page(PageQuery query) {
        int current = query.current();
        int pageSize = query.pageSize();
        List<DataSource> all = new ArrayList<>(store.values());
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
