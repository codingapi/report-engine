package com.codingapi.report.data.datasource;

import java.util.List;

/**
 * 元数据探查结果：一张表/sheet/CSV 文件解析出的列元数据。
 *
 * <p>由 {@link DataExtractor#introspect(DataSource)} 返回，供建模界面选表、推断字段。 DB 类型：每张物理表一个 {@link
 * IntrospectedTable}；EXCEL 类型：每个 sheet 一个；CSV 类型：单个（文件即表）。
 *
 * @param name 表名 / sheet 名 / 文件名
 * @param columns 列元数据（name/dataType/primaryKey/remark）
 * @param alias 数据集别名初值：DB 类型为表备注（JDBC {@code getTables} 的 REMARKS），CSV/Excel 为 null；经 {@code DataSourceService} 用数据源已保存别名覆盖（已保存优先），用作数据集别名默认值
 */
public record IntrospectedTable(String name, List<ColumnMeta> columns, String alias) {}
