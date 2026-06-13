package com.codingapi.report.model.source;

/**
 * 数据源类型 = <b>提取器种类</b>，按"怎么取数"划分，<b>不区分具体数据库厂商</b>。
 *
 * <p>因为计算全在 Java、连接只负责提取，关系型数据库一律走 JDBC，语法层面没有区别，
 * 所以不需要区分 MySQL / Postgres——具体厂商（驱动 / URL / 方言）只是
 * {@link DataSource#getConfig()} 里的连接配置，不构成类型差异。
 *
 * <p>每个类型对应一种提取器实现（见执行层 {@code DataExtractor}），后续可扩展 ES、MONGO 等。
 */
public enum DataSourceType {
    /** 关系型数据库，统一走 JDBC（MySQL / Postgres / Oracle… 厂商差异落在连接配置里） */
    DB,
    /** HTTP 接口（通常返回 JSON） */
    API,
    /** Excel 文件 */
    EXCEL,
    /** CSV 文件 */
    CSV,
    /** JSON 文档 / 文件 */
    JSON
    // 后续可扩展：ES, MONGO, ...
}
