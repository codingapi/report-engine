package com.codingapi.report.repository;

import com.codingapi.report.data.datasource.DataSource;

/**
 * 数据源仓库：以领域对象 {@link DataSource} 存取。
 *
 * <p>framework 层的存储抽象扩展点，分页用 {@link PageQuery}/{@link PageResult}，不依赖任何 Spring 类型，保持 framework
 * 可独立发布。由使用方提供实现（example 提供内存实现作为演示；生产环境由使用方提供持久化实现）。
 *
 * <p>与 {@link ReportRepository}/{@link DataModelRepository}/{@link DataSourceTypeRepository} 同范式： 承载全局数据源连接 +
 * 其下数据集的 CRUD。
 */
public interface DataSourceRepository {

    /** 保存（无 id 则生成），返回数据源 id。 */
    String save(DataSource source);

    /** 按 id 加载，不存在返回 null。 */
    DataSource find(String id);

    /** 分页查询（按 {@link PageQuery}）。 */
    PageResult<DataSource> page(PageQuery query);

    /** 删除指定数据源。 */
    void delete(String id);
}
