package com.example.report.excel.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExcelWorkbookDTO {
    private List<ExcelSheetDTO> sheets;
}
