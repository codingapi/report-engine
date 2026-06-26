package com.codingapi.report.repository;

import com.codingapi.report.core.Report;

/**
 * 报表仓库：以领域对象 {@link Report} 存取报表。
 *
 * <p>framework 层的存储抽象扩展点，分页用 {@link PageQuery}/{@link PageResult}，不依赖任何 Spring 类型，保持 framework
 * 可独立发布。由使用方提供实现（example 提供内存实现作为演示；生产环境由使用方提供持久化实现）。
 */
public interface ReportRepository {

    /** 保存（无 id 则生成），返回报表 id。 */
    String save(Report report);

    /** 按 id 加载，不存在返回 null。 */
    Report find(String id);

    /** 分页查询报表（按 {@link PageQuery}）。 */
    PageResult<Report> page(PageQuery query);

    /** 删除指定报表。 */
    void delete(String id);
}
