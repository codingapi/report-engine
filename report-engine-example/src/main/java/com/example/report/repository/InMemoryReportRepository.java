package com.example.report.repository;

import com.codingapi.report.core.Report;
import com.codingapi.report.repository.PageQuery;
import com.codingapi.report.repository.PageResult;
import com.codingapi.report.repository.ReportRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ReportRepository} 的内存实现（example 演示用）。
 *
 * <p>以领域 {@link Report} 保存；进程内存储，重启丢失。生产环境应由使用方提供持久化实现。
 */
public class InMemoryReportRepository implements ReportRepository {

    private final ConcurrentHashMap<String, Report> store = new ConcurrentHashMap<>();

    @Override
    public String save(Report report) {
        String id =
                report.getId() != null && !report.getId().isBlank()
                        ? report.getId()
                        : UUID.randomUUID().toString();
        report.setId(id);

        long now = System.currentTimeMillis();
        Report existing = store.get(id);
        report.setCreateTime(existing != null ? existing.getCreateTime() : now);
        report.setUpdateTime(now);

        if (report.getCellBindings() == null) report.setCellBindings(List.of());
        if (report.getLoopBlocks() == null) report.setLoopBlocks(List.of());
        if (report.getSummaries() == null) report.setSummaries(List.of());
        if (report.getParameters() == null) report.setParameters(List.of());

        store.put(id, report);
        return id;
    }

    @Override
    public Report find(String id) {
        return store.get(id);
    }

    @Override
    public PageResult<Report> page(PageQuery query) {
        int current = query.current();
        int pageSize = query.pageSize();
        List<Report> all = new ArrayList<>(store.values());
        all.sort((a, b) -> Long.compare(b.getCreateTime(), a.getCreateTime()));
        long total = all.size();
        int from = (int) Math.min((long) (current - 1) * pageSize, total);
        int to = (int) Math.min((long) from + pageSize, total);
        List<Report> pageList = all.subList(from, to);
        return new PageResult<>(pageList, total);
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }
}
