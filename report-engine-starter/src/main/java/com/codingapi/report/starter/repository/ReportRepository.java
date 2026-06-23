package com.codingapi.report.starter.repository;

import com.codingapi.report.config.ReportConfig;
import com.codingapi.springboot.framework.dto.request.SearchRequest;
import org.springframework.data.domain.Page;

/**
 * 报表配置仓库：以强类型 {@link ReportConfig} 实体存取报表配置。
 * <p>
 * 接口为 Spring 感知扩展点（使用 {@link SearchRequest}/{@link Page} 分页），由使用方提供实现
 * （example 提供内存实现作为演示；生产环境由使用方提供持久化实现）。
 * starter 不内置默认实现，由使用方根据需要自行决定如何存储管理。
 */
public interface ReportRepository {

    /** 保存（无 id 则生成），返回报表 id。 */
    String save(ReportConfig report);

    /** 按 id 加载完整配置，不存在返回 null。 */
    ReportConfig find(String id);

    /** 分页查询报表（按 SearchRequest 的 current/pageSize）。 */
    Page<ReportConfig> page(SearchRequest request);

    /** 删除指定报表配置。 */
    void delete(String id);
}
