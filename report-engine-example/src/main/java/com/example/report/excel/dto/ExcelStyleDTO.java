package com.example.report.excel.dto;

import lombok.Data;

@Data
public class ExcelStyleDTO {
    private ExcelFontDTO font;
    private String align;
    private String valign;
    private Boolean wrap;
    private Integer rotation;
    private String fill;
    private ExcelBordersDTO borders;
    private String numberFormat;
    private ExcelPaddingDTO padding;
}
