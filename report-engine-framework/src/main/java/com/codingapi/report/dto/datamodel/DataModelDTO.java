package com.codingapi.report.dto.datamodel;

import java.util.List;

/**
 * 数据模型出入站契约（GET 返回 / POST 保存）。{@code status} 存枚举名；{@code datasources} 为前端展示用的连接视图
 * （由各 TableDataset 自带的连接去重收集，出口脱敏）。
 */
public record DataModelDTO(
        String id,
        String name,
        String status,
        long createTime,
        long updateTime,
        List<DataSourceDTO> datasources,
        List<DatasetDTO> datasets,
        List<RelationshipDTO> relationships) {}