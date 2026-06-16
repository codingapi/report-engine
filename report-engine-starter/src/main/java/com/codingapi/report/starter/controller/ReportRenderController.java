package com.codingapi.report.starter.controller;

import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.data.datasource.csv.CsvDataExtractor;
import com.codingapi.report.excel.ExcelExporter;
import com.codingapi.report.excel.pojo.Workbook;
import com.codingapi.report.param.ParamContext;
import com.codingapi.report.render.Report;
import com.codingapi.report.render.engine.ReportRenderer;
import com.codingapi.report.render.grid.CellBinding;
import com.codingapi.report.render.grid.LoopBlock;
import com.codingapi.report.render.grid.SummaryRow;
import com.codingapi.report.starter.converter.RenderDtoConverter;
import com.codingapi.report.starter.dto.RenderDtos.RenderRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 报表渲染 API：前端配置 + 模板快照 → {@link ReportRenderer} 渲染 → 返回填充数据的 .xlsx。
 * <p>
 * DTO 承接前端 JSON、{@link RenderDtoConverter} 转为领域对象，本类只负责端点编排。
 */
@RestController
@RequestMapping("/api/report")
@ConditionalOnClass(RestController.class)
public class ReportRenderController {

    private final DataModel dataModel;
    private final CsvDataExtractor csvExtractor;

    public ReportRenderController(DataModel dataModel, CsvDataExtractor csvExtractor) {
        this.dataModel = dataModel;
        this.csvExtractor = csvExtractor;
    }

    @PostMapping("/render")
    public ResponseEntity<byte[]> render(@RequestBody RenderRequest request) {
        ReportRenderer renderer = new ReportRenderer(List.of(csvExtractor));

        List<CellBinding> bindings = RenderDtoConverter.convertBindings(request.cellBindings());
        List<LoopBlock> loops = RenderDtoConverter.convertLoops(request.loopBlocks());
        List<SummaryRow> summaries = RenderDtoConverter.convertSummaries(request.summaries());

        Report report = Report.builder()
                .id("render-" + System.currentTimeMillis())
                .name("报表导出")
                .dataModelId(dataModel.getId())
                .cellBindings(bindings)
                .loopBlocks(loops)
                .summaries(summaries)
                .build();

        Workbook template = request.template();
        Map<String, Object> paramValues = request.params() != null ? request.params() : Map.of();
        Workbook result = renderer.render(dataModel, report, new ParamContext(paramValues), template);
        byte[] xlsx = new ExcelExporter().export(result);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(xlsx.length)
                .body(xlsx);
    }
}
