package com.example.report.excel.dto;

import lombok.Data;

@Data
public class ExcelRichTextSegmentDTO {
    private String text;
    private ExcelFontDTO style;
}
