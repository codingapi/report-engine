package com.example.report.excel.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;

@Data
public class ExcelSheetDTO {
    private String id;
    private String name;
    private int rowCount;
    private int columnCount;
    private double defaultRowHeight = 24;
    private double defaultColumnWidth = 88;
    private List<ExcelMergeDTO> merges;
    private List<ExcelCellDTO> cells;
    private List<ExcelRowDTO> rows;
    private List<ExcelColumnDTO> columns;
    private List<JsonNode> loopBlocks;
}
