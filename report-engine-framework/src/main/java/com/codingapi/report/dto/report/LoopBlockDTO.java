package com.codingapi.report.dto.report;

public record LoopBlockDTO(
        String id,
        String label,
        String sheetId,
        int startRow,
        int startColumn,
        int endRow,
        int endColumn,
        SourceDTO source) {}
