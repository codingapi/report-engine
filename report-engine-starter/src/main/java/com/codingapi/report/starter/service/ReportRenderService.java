package com.codingapi.report.starter.service;

import com.codingapi.report.core.RenderDtoConverter;
import com.codingapi.report.core.Report;
import com.codingapi.report.core.engine.DrillCollector;
import com.codingapi.report.core.engine.ReportRenderer;
import com.codingapi.report.core.grid.CellBinding;
import com.codingapi.report.core.grid.LoopBlock;
import com.codingapi.report.core.grid.SummaryRow;
import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.datasource.DataExtractor;
import com.codingapi.report.excel.pojo.Workbook;
import com.codingapi.report.param.ParamContext;
import com.codingapi.report.starter.dto.RenderDtos.DrillRequest;
import com.codingapi.report.starter.dto.RenderDtos.DrillResult;
import com.codingapi.report.starter.dto.RenderDtos.FieldInfo;
import com.codingapi.report.starter.dto.RenderDtos.PreviewResult;
import com.codingapi.report.starter.dto.RenderDtos.RenderRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表渲染业务：render/preview/drill 编排 + drill 反查投影去重。
 *
 * <p>数据模型从 {@link DataModelService#loadDataModel} 加载，提取器走 {@code List<DataExtractor>} 注册表派发。
 * Controller 只做 HTTP 编排（xlsx 导出 / 响应包装），渲染逻辑全部在此。
 */
public class ReportRenderService {

    private final DataModelService dataModelService;
    private final List<DataExtractor> extractors;

    public ReportRenderService(DataModelService dataModelService, List<DataExtractor> extractors) {
        this.dataModelService = dataModelService;
        this.extractors = extractors;
    }

    /** 渲染为工作簿（{@code /render} 端点再导出 xlsx）。 */
    public Workbook render(RenderRequest request) {
        DataModel dm = dataModelService.loadDataModel(request.dataModelId());
        return renderWorkbook(dm, request, null);
    }

    /** 预览：渲染并返回工作簿 + 反查格坐标列表。 */
    public PreviewResult preview(RenderRequest request) {
        DataModel dm = dataModelService.loadDataModel(request.dataModelId());
        DrillCollector collector = new DrillCollector();
        Workbook workbook = renderWorkbook(dm, request, collector);
        List<String> drillable = new ArrayList<>(collector.getAll().keySet());
        return new PreviewResult(workbook, drillable);
    }

    /** 反查明细：渲染 + 投影目标格贡献的原始行（按主键去重）。 */
    public DrillResult drill(DrillRequest request) {
        DataModel dm = dataModelService.loadDataModel(request.request().dataModelId());
        DrillCollector collector = new DrillCollector();
        renderWorkbook(dm, request.request(), collector);

        DrillCollector.DrillInfo info = collector.get(request.row(), request.col());
        if (info == null) {
            return new DrillResult(null, null, List.of(), List.of());
        }

        String datasetId = info.getDrillView();
        Dataset dataset =
                dm.getDatasets().stream()
                        .filter(d -> d.getId().equals(datasetId))
                        .findFirst()
                        .orElse(null);
        if (dataset == null) {
            return new DrillResult(datasetId, null, List.of(), List.of());
        }

        // 投影：从组合行中提取该数据集的字段，按主键去重
        List<Map<String, Object>> projected = new ArrayList<>();
        List<String> seenKeys = new ArrayList<>();
        for (Map<String, Object> row : info.getRows()) {
            Map<String, Object> projRow = new LinkedHashMap<>();
            StringBuilder keyBuilder = new StringBuilder();
            for (com.codingapi.report.data.dataset.Field field : dataset.getFields()) {
                String qualifiedName = datasetId + "." + field.getName();
                Object value = row.get(qualifiedName);
                if (value != null) {
                    projRow.put(field.getName(), value);
                    if (field.isPrimaryKey()) {
                        keyBuilder.append(value).append(" ");
                    }
                }
            }
            String key =
                    keyBuilder.length() > 0
                            ? keyBuilder.toString()
                            : String.valueOf(projected.size());
            if (!seenKeys.contains(key)) {
                seenKeys.add(key);
                projected.add(projRow);
            }
        }

        return new DrillResult(
                datasetId,
                dataset.getAlias(),
                dataset.getFields().stream()
                        .map(f -> new FieldInfo(f.getName(), f.getAlias()))
                        .toList(),
                projected);
    }

    /** 配置 + 模板快照 → 领域对象 → 渲染（render/preview/drill 共用）。 */
    private Workbook renderWorkbook(
            DataModel dm, RenderRequest request, DrillCollector drillCollector) {
        ReportRenderer renderer = new ReportRenderer(extractors);

        List<CellBinding> bindings = RenderDtoConverter.convertBindings(request.cellBindings());
        List<LoopBlock> loops = RenderDtoConverter.convertLoops(request.loopBlocks());
        List<SummaryRow> summaries = RenderDtoConverter.convertSummaries(request.summaries());

        Report report =
                Report.builder()
                        .id("render-" + System.currentTimeMillis())
                        .name("报表导出")
                        .dataModelId(dm.getId())
                        .cellBindings(bindings)
                        .loopBlocks(loops)
                        .summaries(summaries)
                        .build();

        Workbook template = request.template();
        Map<String, Object> paramValues = request.params() != null ? request.params() : Map.of();
        return renderer.render(dm, report, new ParamContext(paramValues), template, drillCollector);
    }
}
