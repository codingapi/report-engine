package com.codingapi.report.starter.dto;

import com.codingapi.report.excel.pojo.Workbook;

import java.util.List;
import java.util.Map;

/**
 * 渲染请求的 DTO 契约（前端 JSON → 这些 record → 领域对象）。
 * <p>
 * {@code Value} 等 sealed interface 未加 Jackson 多态注解，故用 DTO 中间层承接前端 JSON，
 * 再由 {@code RenderDtoConverter} 转为 framework 领域对象。所有 record 集中于此容器类。
 */
public final class RenderDtos {

    private RenderDtos() {
    }

    public record RenderRequest(
            List<BindingDTO> cellBindings,
            List<LoopBlockDTO> loopBlocks,
            List<SummaryRowDTO> summaries,
            Map<String, Object> params,
            Workbook template) {
    }

    public record BindingDTO(
            String cellKey,
            ValueDTO value,
            String expansion,
            String expandMode,
            boolean mergeRepeated,
            String parentCell,
            List<ConditionDTO> conditions,
            String preview) {
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

    public record SummaryRowDTO(FieldRefDTO groupBy, int fromColumn, int toColumn, List<SummaryCellDTO> cells, Integer row) {
    }

    public record FieldRefDTO(String datasetId, String field) {
    }

    public record SummaryCellDTO(int column, ValueDTO value, String kind, String payload, String aggregation, String preview) {
    }
}
