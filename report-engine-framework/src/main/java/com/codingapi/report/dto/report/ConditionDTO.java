package com.codingapi.report.dto.report;

public record ConditionDTO(String id, ValueDTO left, String operator, ValueDTO right) {}
