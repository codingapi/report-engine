package com.example.report.repository;

import com.codingapi.report.config.ReportConfig;
import com.codingapi.report.starter.repository.ReportRepository;
import com.codingapi.springboot.framework.dto.request.SearchRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ReportRepository} 的内存实现（example 演示用）。
 * <p>
 * 报表配置以强类型 {@link ReportConfig} 实体保存；进程内存储，重启丢失。
 * 生产环境应由使用方提供持久化实现。
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
    public Page<ReportConfig> page(SearchRequest request) {
        int current = Math.max(request.getCurrent(), 1);
        int pageSize = request.getPageSize() > 0 ? request.getPageSize() : 10;
        List<ReportConfig> all = new ArrayList<>(store.values());
        long total = all.size();
        int from = (int) Math.min((long) (current - 1) * pageSize, total);
        int to = (int) Math.min((long) from + pageSize, total);
        List<ReportConfig> pageList = all.subList(from, to);
        Pageable pageable = PageRequest.of(current - 1, pageSize);
        return new PageImpl<>(pageList, pageable, total);
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }
}
