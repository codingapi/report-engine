package com.codingapi.report.dto.report;


import java.util.List;

public record SourceDTO(
        String datasetId,
        List<ConditionDTO> filters,
        List<String> groupBy,
        List<String> orderBy) {}
