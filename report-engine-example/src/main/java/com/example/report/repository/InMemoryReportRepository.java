package com.example.report.repository;

import com.codingapi.report.config.ReportConfig;
import com.codingapi.report.repository.PageQuery;
import com.codingapi.report.repository.PageResult;
import com.codingapi.report.repository.ReportRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ReportRepository} 的内存实现（example 演示用）。
 *
 * <p>报表配置以强类型 {@link ReportConfig} 实体保存；进程内存储，重启丢失。 生产环境应由使用方提供持久化实现。
 */
public class InMemoryReportRepository implements ReportRepository {

    private final Map<String, ReportConfig> store = new ConcurrentHashMap<>();

    @Override
    public String save(ReportConfig report) {
        Object idObj = report.getId();
        String id = (idObj instanceof String s && !s.isBlank()) ? s : UUID.randomUUID().toString();
        report.setId(id);

        long now = System.currentTimeMillis();
        ReportConfig existing = store.get(id);
        report.setCreateTime(existing != null ? existing.getCreateTime() : now);
        report.setUpdateTime(now);

        // null 列表归一为空，避免前端拿到 null
        if (report.getCellBindings() == null) report.setCellBindings(List.of());
        if (report.getLoopBlocks() == null) report.setLoopBlocks(List.of());
        if (report.getSummaries() == null) report.setSummaries(List.of());
        if (report.getParams() == null) report.setParams(List.of());

        store.put(id, report);
        return id;
    }

    @Override
    public ReportConfig find(String id) {
        return store.get(id);
    }

    @Override
    public PageResult<ReportConfig> page(PageQuery query) {
        int current = query.current();
        int pageSize = query.pageSize();
        List<ReportConfig> all = new ArrayList<>(store.values());
        // 按创建时间倒序排列
        all.sort((a, b) -> Long.compare(b.getCreateTime(), a.getCreateTime()));
        long total = all.size();
        int from = (int) Math.min((long) (current - 1) * pageSize, total);
        int to = (int) Math.min((long) from + pageSize, total);
        List<ReportConfig> pageList = all.subList(from, to);
        return new PageResult<>(pageList, total);
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }
}
