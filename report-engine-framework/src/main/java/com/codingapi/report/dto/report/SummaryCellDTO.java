package com.codingapi.report.dto.report;

public record SummaryCellDTO(
        int crossPos,
        ValueDTO value,
        String kind,
        String payload,
        String aggregation,
        String preview,
        boolean drillEnabled,
        String drillView) {}
