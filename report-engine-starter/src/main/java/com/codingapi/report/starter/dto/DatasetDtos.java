package com.codingapi.report.starter.dto;

import java.util.List;
import java.util.Map;

/** 数据集元数据/预览的响应 DTO 契约（供 {@code DatasetController} 列表与预览端点）。 */
public final class DatasetDtos {

    private DatasetDtos() {}

    public record DatasetDTO(
            String id,
            String alias,
            String dataSourceId,
            String dataSourceType,
            List<FieldDTO> fields) {}

    public record FieldDTO(String name, String alias, String dataType, boolean primaryKey) {}

    public record PreviewDTO(List<String> columns, List<Map<String, Object>> rows) {}
}
