package com.codingapi.report.repository;

import com.codingapi.report.data.datamodel.DataModel;

/**
 * 数据模型仓库：以领域对象 {@link DataModel} 存取可复用的数据模型。
 *
 * <p>framework 层的存储抽象扩展点，分页用 {@link PageQuery}/{@link PageResult}，不依赖任何 Spring 类型，保持 framework
 * 可独立发布。由使用方提供实现（example 提供内存实现作为演示；生产环境由使用方提供持久化实现，落盘加密等亦在该层处理）。
 *
 * <p>与 {@link ReportRepository} 同范式：报表只存 {@code dataModelId} 引用，数据模型本身独立存取、多处复用。
 */
public interface DataModelRepository {

    /** 保存（无 id 则生成），返回数据模型 id。 */
    String save(DataModel dataModel);

    /** 按 id 加载，不存在返回 null。 */
    DataModel find(String id);

    /** 分页查询数据模型（按 {@link PageQuery}）。 */
    PageResult<DataModel> page(PageQuery query);

    /** 删除指定数据模型。 */
    void delete(String id);
}
