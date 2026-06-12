package com.example.report.excel.dto;

import lombok.Data;

@Data
public class ExcelBordersDTO {
    private ExcelBorderDTO top;
    private ExcelBorderDTO right;
    private ExcelBorderDTO bottom;
    private ExcelBorderDTO left;
}
