package com.example.report.excel.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;

@Data
public class ExcelMergeDTO {
    private int startRow;
    private int startCol;
    private int rowSpan;
    private int colSpan;
    private List<JsonNode> props;
}
