package com.codingapi.report.dto.report;


import java.util.List;

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
        String drillView) {}
