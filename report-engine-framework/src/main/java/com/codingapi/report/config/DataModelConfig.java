package com.codingapi.report.config;

import com.codingapi.report.config.dto.DataModelDtos.DataSourceDTO;
import com.codingapi.report.config.dto.DataModelDtos.DatasetDTO;
import com.codingapi.report.config.dto.DataModelDtos.RelationshipDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Data;

/**
 * 数据模型配置实体（持久化契约）：可复用语义层的强类型 POJO，描述"数据长什么样、表之间怎么关联"， 与具体报表无关，建一次可被多个报表引用。
 *
 * <p>字段含元数据（id/name/status/createTime/updateTime）与内容（datasources/datasets/relationships）。 内容引用
 * {@link DataModelDtos} 的 DTO record（Jackson 可序列化，不依赖无注解的 sealed {@code Dataset}）。
 *
 * <p>{@code resolvedDataModel} 为响应富化字段：仅 {@code GET /api/datamodels/{id}} 返回时由 datasource 模块
 * 填充解密后的运行时 {@link com.codingapi.report.data.datamodel.DataModel} 视图，不参与持久化 （{@link
 * JsonInclude.Include#NON_NULL} 省略空值）。
 *
 * <p>存储交给 {@link com.codingapi.report.repository.DataModelRepository}（使用方提供实现）。
 */
@Data
public class DataModelConfig {

    private String id;

    private String name;

    /** 状态：{@code "DRAFT"}/{@code "PUBLISHED"}（String 存枚举名，保持实体不依赖 Spring） */
    private String status;

    /** 创建时间（epoch 毫秒） */
    private long createTime;

    /** 修改时间（epoch 毫秒） */
    private long updateTime;

    /** 连接列表（config 含加密后的敏感字段） */
    private List<DataSourceDTO> datasources;

    /** 数据集列表（TableDataset / UnionDataset 两种形态，用 DatasetDTO.kind 区分） */
    private List<DatasetDTO> datasets;

    /** 跨数据集关联关系 */
    private List<RelationshipDTO> relationships;

    /** 响应富化字段：仅加载时填充解密后的运行时 DataModel，不持久化 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object resolvedDataModel;
}
