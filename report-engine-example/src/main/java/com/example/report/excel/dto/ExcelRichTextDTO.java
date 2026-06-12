package com.example.report.excel.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExcelRichTextDTO {
    private String text;
    private List<ExcelRichTextSegmentDTO> segments;
}
