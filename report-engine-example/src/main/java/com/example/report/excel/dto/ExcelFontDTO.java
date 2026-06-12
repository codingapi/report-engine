package com.example.report.excel.dto;

import lombok.Data;

@Data
public class ExcelFontDTO {
    private String family;
    private Double size;
    private Boolean bold;
    private Boolean italic;
    private Boolean underline;
    private Boolean strikethrough;
    private String color;
}
