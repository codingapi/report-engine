package com.codingapi.report.repository;

import com.codingapi.report.data.datasource.DataSourceTypeConfig;

/**
 * 数据源类型配置仓库：以领域对象 {@link DataSourceTypeConfig} 存取。
 *
 * <p>framework 层的存储抽象扩展点，分页用 {@link PageQuery}/{@link PageResult}，不依赖任何 Spring 类型，保持 framework
 * 可独立发布。由使用方提供实现（example 提供内存实现作为演示；生产环境由使用方提供持久化实现）。
 *
 * <p>与 {@link ReportRepository}/{@link DataModelRepository} 同范式：当前主要承载 DB 驱动配置。
 */
public interface DataSourceTypeRepository {

    /** 保存（无 id 则生成），返回配置 id。 */
    String save(DataSourceTypeConfig config);

    /** 按 id 加载，不存在返回 null。 */
    DataSourceTypeConfig find(String id);

    /** 分页查询（按 {@link PageQuery}）。 */
    PageResult<DataSourceTypeConfig> page(PageQuery query);

    /** 删除指定配置。 */
    void delete(String id);
}
