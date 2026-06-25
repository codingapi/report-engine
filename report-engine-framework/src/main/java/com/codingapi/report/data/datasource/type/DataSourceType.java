package com.codingapi.report.data.datasource.type;

import java.util.Map;

/**
 * 数据源类型：约定每种类型的 <b>类型级 metadata 配置</b>，由枚举升级为 sealed interface。
 *
 * <p>不同类型携带不同的类型级配置（"这一类数据源本身需要什么"）：
 *
 * <ul>
 *   <li>{@link DbDataSourceType} —— 驱动 jar + 驱动类
 *   <li>{@link ExcelDataSourceType} —— 上传文件的存储路径
 *   <li>{@link CsvDataSourceType} —— 文件存储路径（测试/示例内置）
 * </ul>
 *
 * <p>而 <b>连接实例级配置</b>（DB 的 url/账号/密码/schema、文件的具体路径）放在 {@code DataSource.config}。
 *
 * <p>{@link #type()} 返回判别串（{@code "DB"}/{@code "EXCEL"}/{@code "CSV"}），供提取器匹配与持久化序列化—— 实现固定，无需额外枚举。
 *
 * <p>三个实现都在本包内：classpath（unnamed module）下 sealed 类型的许可子类必须与之同包。
 */
public sealed interface DataSourceType
        permits CsvDataSourceType, ExcelDataSourceType, DbDataSourceType {

    /** 类型判别串："DB"/"EXCEL"/"CSV"。 */
    String type();

    /**
     * 由判别串 + 配置 Map 还原类型对象（持久化/DTO 入站用）。
     *
     * <p>过渡期：类型级配置（jarFile/driverClass/storagePath）与连接实例级配置共存于同一 {@code config} Map，
     * 后续 DTO 重构会把类型级配置独立出来。
     */
    static DataSourceType of(String type, Map<String, Object> config) {
        Map<String, Object> c = config != null ? config : Map.of();
        return switch (type) {
            case "CSV" -> new CsvDataSourceType(str(c, "storagePath"));
            case "EXCEL" -> new ExcelDataSourceType(str(c, "storagePath"));
            case "DB" -> new DbDataSourceType(str(c, "jarFile"), str(c, "driverClass"));
            default -> throw new IllegalArgumentException("未知数据源类型: " + type);
        };
    }

    private static String str(Map<String, Object> config, String key) {
        Object v = config.get(key);
        return v != null ? v.toString() : null;
    }
}
