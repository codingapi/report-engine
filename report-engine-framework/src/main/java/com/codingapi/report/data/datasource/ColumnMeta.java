package com.codingapi.report.data.datasource;

/**
 * 列元数据：{@link DataExtractor#listColumns(DataSource, String)} 的返回项。
 *
 * <p>用于数据集建模时"选表 → 探查列"：从物理数据源读出列名/原生类型/主键标记， 供前端展示并映射到 {@link
 * com.codingapi.report.data.dataset.DataType}。
 *
 * @param name 物理列名（与数据库/文件表头一致）
 * @param dataType 数据源原生类型名（如 {@code VARCHAR}/{@code INTEGER}/{@code TIMESTAMP}）， 由提取器返回，映射到业务
 *     {@link com.codingapi.report.data.dataset.DataType} 由建模层完成
 * @param primaryKey 是否主键（来自 JDBC {@code getPrimaryKeys} 或外键元数据）
 */
public record ColumnMeta(String name, String dataType, boolean primaryKey) {}
