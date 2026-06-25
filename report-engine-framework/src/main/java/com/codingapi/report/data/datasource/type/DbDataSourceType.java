package com.codingapi.report.data.datasource.type;

/**
 * 关系型数据库数据源类型。
 *
 * <p>{@code jarFile} 为上传的驱动 jar（落在系统配置的 driver 目录下），{@code driverClass} 为从 jar 解析后由用户选定的驱动类。
 * 连接实例级的 url/账号/密码/schema 放在 {@code DataSource.config}。
 */
public record DbDataSourceType(String jarFile, String driverClass) implements DataSourceType {

    @Override
    public String type() {
        return "DB";
    }
}
