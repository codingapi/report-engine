package com.codingapi.report.starter.repository;

import java.util.List;
import java.util.Map;

/**
 * 报表配置仓库：以原样 JSON（Map）存取报表配置。
 * <p>
 * 配置包含 name/cellBindings/loopBlocks/summaries/params/template，
 * 引擎不解析其结构，仅做存取；渲染走独立的 {@code /api/report/render} 接口。
 * <p>
 * starter 提供默认内存实现（见自动配置），使用方（如 example）可提供自己的持久化实现覆盖。
 */
public interface ReportRepository {

    /** 保存（无 id 则生成），返回报表 id。 */
    String save(Map<String, Object> config);

    /** 按 id 加载完整配置，不存在返回 null。 */
    Map<String, Object> find(String id);

    /** 列出全部报表的完整配置。 */
    List<Map<String, Object>> all();
}
