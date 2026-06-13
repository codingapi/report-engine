package com.codingapi.report.model.source;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 连接层：一个物理数据连接的抽象（数据库 / HTTP API / Excel 文件 / CSV 文件 / JSON）。
 *
 * <h3>定位：数据流的最上游</h3>
 * <pre>
 *   DataSource（连接）→ Dataset（表/查询）→ Report（报表）
 * </pre>
 * 一个 DataSource 对应一个物理连接，下面可挂多个 {@link Dataset}（比如一个 PostgreSQL 库
 * 下选了 10 张表，就有 1 个 DataSource + 10 个 Dataset）。
 *
 * <h3>为什么报表不直接绑定 DataSource？</h3>
 * <p>一个连接里可能有几百张表，但一张报表通常只用几张。如果报表直接引用 DataSource，
 * 要么暴露全部表（信息过载），要么在报表里再写一遍"选哪些表"（重复）。
 * 通过中间的 {@link Dataset} 层，选表动作在建模时完成一次，报表只引用 datasetId。
 *
 * <h3>连接配置为什么用 {@code Map<String, Object>}？</h3>
 * <p>不同类型的连接，配置项完全不同：
 * <ul>
 *   <li>DB：host, port, database, username, password, driver</li>
 *   <li>API：url, headers, authMethod</li>
 *   <li>CSV/Excel：path（classpath 路径或文件路径）</li>
 * </ul>
 * 用 Map 而非强类型字段，让 DataSource 对新增连接类型保持开放，
 * 具体的 config key 约定由对应的 {@code DataExtractor} 实现类定义。
 * 这些配置是基础设施层面的，不序列化进报表模板。
 */
@Data
@Builder
public class DataSource {
    private String id;
    private String name;
    /**
     * 连接类型 = 提取器种类（DB/API/EXCEL/CSV/JSON）。
     * 按"怎么取数"划分，不区分具体数据库厂商（MySQL/PostgreSQL 都是 DB）。
     */
    private DataSourceType type;
    /**
     * 连接配置（host/库名/密码/文件路径等），不进报表模板。
     * <p>具体 key 由对应 DataExtractor 约定，例如：
     * <ul>
     *   <li>CSV: {@code {path: "data/employees.csv"}}</li>
     *   <li>DB: {@code {host: "localhost", port: 5432, database: "hr", ...}}</li>
     * </ul>
     */
    private Map<String, Object> config;
}
