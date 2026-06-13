package com.example.report.model.source;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 连接层：一个物理连接（库 / API / Excel）。
 *
 * <p>一个连接下可有多张表。报表<b>不直接绑定</b>它，而是绑定从中选出的 {@link Dataset}。
 * 连接配置（host/库名/密码）与报表模板解耦，模板只引用 datasetId。
 */
@Data
@Builder
public class DataSource {
    private String id;
    private String name;
    private DataSourceType type;
    /** 连接配置（host/库名/密码 等），不进报表模板 */
    private Map<String, Object> config;
}
