package com.example.report.excel;

import com.example.report.excel.dto.ExcelWorkbookDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/excel")
public class ExcelController {

    private final ExcelBuilderService excelBuilderService;

    public ExcelController(ExcelBuilderService excelBuilderService) {
        this.excelBuilderService = excelBuilderService;
    }

    @PostMapping("/generate")
    public ResponseEntity<byte[]> generateExcel(@RequestBody ExcelWorkbookDTO workbook) {
        byte[] xlsxBytes = excelBuilderService.buildExcel(workbook);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "report.xlsx");
        headers.setContentLength(xlsxBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(xlsxBytes);
    }
}
