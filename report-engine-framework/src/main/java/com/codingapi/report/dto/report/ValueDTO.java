package com.codingapi.report.dto.report;


import java.util.List;

public record ValueDTO(
        String type,
        String payload,
        String aggregation,
        ValueDTO operand,
        String funcName,
        List<ValueDTO> args,
        List<PartDTO> parts) {}
