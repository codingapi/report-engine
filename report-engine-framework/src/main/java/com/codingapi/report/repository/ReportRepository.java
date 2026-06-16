package com.codingapi.report.repository;

import java.util.List;
import java.util.Map;

/**
 * 报表配置仓库：以原样 JSON（Map）存取报表配置。
 * <p>
 * 配置包含 name/cellBindings/loopBlocks/summaries/params/template，
 * 引擎不解析其结构，仅做存取；渲染走独立的 {@code /api/report/render} 接口。
 * <p>
 * framework 提供 {@link InMemoryReportRepository} 默认实现；使用方可提供自己的持久化实现，
 * 由 starter 自动配置以 {@code @ConditionalOnMissingBean} 装配。
 */
public interface ReportRepository {

    /** 保存（无 id 则生成），返回报表 id。 */
    String save(Map<String, Object> config);

    /** 按 id 加载完整配置，不存在返回 null。 */
    Map<String, Object> find(String id);

    /** 列出全部报表的完整配置。 */
    List<Map<String, Object>> all();
}
