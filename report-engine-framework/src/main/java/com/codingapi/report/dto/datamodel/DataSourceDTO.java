package com.codingapi.report.dto.datamodel;

import java.util.List;
import java.util.Map;

/**
 * 数据源（连接）持久化契约。{@code config} 含加密后的敏感字段； {@code typeConfigId} 引用 DB 驱动配置（EXCEL/CSV 可空）。
 *
 * <p>{@code datasets} 为该连接下解析/维护的数据集（表别名、字段别名等），用于数据源管理界面的数据集维护与编辑回填； DataModel
 * 链路构造时为 {@code null}（数据集在 DataModel 自身管理）。
 */
public record DataSourceDTO(
        String id,
        String name,
        String type,
        String typeConfigId,
        Map<String, Object> config,
        List<DatasetDTO> datasets) {

    /** 兼容旧调用：不带 datasets 的 5 参构造（DataModel 链路 / 既有测试）。 */
    public DataSourceDTO(
            String id, String name, String type, String typeConfigId, Map<String, Object> config) {
        this(id, name, type, typeConfigId, config, null);
    }
}
