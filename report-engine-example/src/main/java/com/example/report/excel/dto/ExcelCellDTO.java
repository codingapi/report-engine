package com.example.report.excel.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;

@Data
public class ExcelCellDTO {
    private int row;
    private int col;
    private String ref;
    private JsonNode value;
    private String formula;
    private ExcelRichTextDTO richText;
    private ExcelStyleDTO style;
    private List<JsonNode> props;
}
