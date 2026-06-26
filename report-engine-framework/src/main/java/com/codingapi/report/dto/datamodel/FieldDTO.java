package com.codingapi.report.dto.datamodel;

/** 字段定义持久化契约。 */
public record FieldDTO(String name, String alias, String dataType, boolean primaryKey) {}
