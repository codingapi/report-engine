package com.codingapi.report.data.datasource.type;

/**
 * CSV 数据源类型。
 *
 * <p>{@code storagePath} 为可选的文件存储根目录；经典 classpath 用法下，具体 CSV 路径放在 {@code DataSource.config} 的
 * {@code path} 键，{@code storagePath} 可为 null。
 */
public record CsvDataSourceType(String storagePath) implements DataSourceType {

    @Override
    public String type() {
        return "CSV";
    }
}
