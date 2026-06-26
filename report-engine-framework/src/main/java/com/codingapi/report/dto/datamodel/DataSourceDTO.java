package com.codingapi.report.dto.datamodel;

import java.util.Map;

/** 数据源（连接）持久化契约。{@code config} 含加密后的敏感字段。 */
public record DataSourceDTO(String id, String name, String type, Map<String, Object> config) {}
