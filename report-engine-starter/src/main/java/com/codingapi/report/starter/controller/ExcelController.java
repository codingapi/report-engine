package com.codingapi.report.starter.controller;

import com.codingapi.report.excel.ExcelExporter;
import com.codingapi.report.excel.ExcelImporter;
import com.codingapi.report.excel.pojo.Workbook;
import com.codingapi.springboot.framework.dto.response.SingleResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Excel 导入导出 API。
 * <p>
 * 仅当 classpath 中存在 Spring Web（RestController）时自动注册。
 * </p>
 */
@RestController
@RequestMapping("/api/excel")
@ConditionalOnClass(RestController.class)
public class ExcelController {

    private final ExcelExporter excelExporter = new ExcelExporter();
    private final ExcelImporter excelImporter = new ExcelImporter();

    @PostMapping("/generate")
    public ResponseEntity<byte[]> generateExcel(@RequestBody Workbook workbook) {
        byte[] xlsxBytes = excelExporter.export(workbook);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "report.xlsx");
        headers.setContentLength(xlsxBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(xlsxBytes);
    }

    @PostMapping("/import")
    public SingleResponse<Workbook> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        return SingleResponse.of(excelImporter.importFrom(file.getInputStream()));
    }
}
