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
import com.codingapi.springboot.framework.dto.response.SingleResponse;
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
 * 报表渲染 API：前端配置 + 模板快照 → {@link ReportRenderer} 渲染。
 * <p>
 * 两个出口共用同一渲染结果 {@link Workbook}：
 * <ul>
 *   <li>{@code /render} —— 经 {@link ExcelExporter} 转 .xlsx 字节流下载</li>
 *   <li>{@code /preview} —— 直接返回 {@link Workbook} JSON，供前端 HTML 渲染网页预览</li>
 * </ul>
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
        Workbook result = renderWorkbook(request);
        byte[] xlsx = new ExcelExporter().export(result);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(xlsx.length)
                .body(xlsx);
    }

    /**
     * 网页预览：与 {@code /render} 共用同一渲染结果，但直接返回 {@link Workbook} JSON，
     * 不经 {@link ExcelExporter}。前端据此用 HTML 表格渲染预览。
     */
    @PostMapping("/preview")
    public SingleResponse<Workbook> preview(@RequestBody RenderRequest request) {
        return SingleResponse.of(renderWorkbook(request));
    }

    /** 配置 + 模板快照 → 领域对象 → 渲染为 {@link Workbook}（render/preview 共用）。 */
    private Workbook renderWorkbook(RenderRequest request) {
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
        return renderer.render(dataModel, report, new ParamContext(paramValues), template);
    }
}
