package com.codingapi.report.config.dto;

import java.util.List;

/**
 * 报表配置的 DTO 契约容器（前端 JSON ↔ 这些 record ↔ framework 领域对象）。
 * <p>
 * {@code Value} 等 sealed interface 未加 Jackson 多态注解，故用这些 record 承接前端 JSON，
 * 再由 starter 的 {@code RenderDtoConverter} 转为 framework 领域对象。
 * <p>
 * 这些 record 同时是 {@code ReportConfig} 实体的字段类型（持久化契约），
 * 保证实体全字段强类型且 Jackson 可序列化（便于落库 JSON）。
 */
public final class ConfigDtos {

    private ConfigDtos() {
    }

    public record BindingDTO(
            String cellKey,
            ValueDTO value,
            String expansion,
            String expandMode,
            boolean mergeRepeated,
            String parentCell,
            List<ConditionDTO> conditions,
            boolean independent,
            String preview,
            boolean drillEnabled,
            String drillView) {
    }

    public record ValueDTO(
            String type,
            String payload,
            String aggregation,
            ValueDTO operand,
            String funcName,
            List<ValueDTO> args,
            List<PartDTO> parts) {
    }

    public record PartDTO(String kind, String text, ValueDTO value) {
    }

    public record ConditionDTO(String id, ValueDTO left, String operator, ValueDTO right) {
    }

    public record LoopBlockDTO(
            String id, String label, String sheetId,
            int startRow, int startColumn, int endRow, int endColumn,
            SourceDTO source) {
    }

    public record SourceDTO(
            String datasetId,
            List<ConditionDTO> filters,
            List<String> groupBy,
            List<String> orderBy) {
    }

    /**
     * 汇总持久化契约。{@code axis} 为 "VERTICAL"/"HORIZONTAL"，null 视为 VERTICAL（向后兼容）。
     * 坐标按轴转置：{@code mainPos} 主轴声明位置（纵向=行/横向=列）、{@code crossFrom/crossTo} 交叉区间（纵向=列/横向=行）。
     */
    public record SummaryRowDTO(String id, String axis, FieldRefDTO groupBy, int crossFrom, int crossTo, List<SummaryCellDTO> cells, Integer mainPos) {
    }

    public record FieldRefDTO(String datasetId, String field) {
    }

    public record SummaryCellDTO(int crossPos, ValueDTO value, String kind, String payload, String aggregation, String preview,
                                  boolean drillEnabled, String drillView) {
    }
}
