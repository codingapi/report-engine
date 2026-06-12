package com.example.report.excel;

import com.codingapi.report.excel.ExcelExporter;
import com.codingapi.report.excel.ExcelImporter;
import com.codingapi.report.excel.pojo.Workbook;
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

@RestController
@RequestMapping("/api/excel")
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
    public Workbook importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        return excelImporter.importFrom(file.getInputStream());
    }
}
