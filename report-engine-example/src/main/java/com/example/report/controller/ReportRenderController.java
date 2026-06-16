package com.example.report.controller;

import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.data.dataset.Query;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.relation.Relationship;
import com.codingapi.report.data.datasource.csv.CsvDataExtractor;
import com.codingapi.report.excel.ExcelExporter;
import com.codingapi.report.excel.pojo.Workbook;
import com.codingapi.report.expression.Templates;
import com.codingapi.report.expression.Value;
import com.codingapi.report.operator.aggregation.Aggregation;
import com.codingapi.report.operator.condition.CompareOperator;
import com.codingapi.report.operator.condition.Condition;
import com.codingapi.report.param.ParamContext;
import com.codingapi.report.render.Report;
import com.codingapi.report.render.engine.ReportRenderer;
import com.codingapi.report.render.grid.CellBinding;
import com.codingapi.report.render.grid.CellRef;
import com.codingapi.report.render.grid.ExpandMode;
import com.codingapi.report.render.grid.Expansion;
import com.codingapi.report.render.grid.LoopBlock;
import com.codingapi.report.render.grid.SummaryCell;
import com.codingapi.report.render.grid.SummaryRow;
import com.codingapi.springboot.framework.dto.response.SingleResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/report")
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

        List<CellBinding> bindings = convertBindings(request.cellBindings());
        List<LoopBlock> loops = convertLoops(request.loopBlocks());
        List<SummaryRow> summaries = convertSummaries(request.summaries());

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

    /**
     * 返回当前报表所用的数据模型：数据集（含字段）+ 数据关系。
     * 报表参数属报表级配置，由前端管理，不在此返回。
     */
    @GetMapping("/datamodel")
    public SingleResponse<DataModelDTO> datamodel() {
        List<DatasetInfoDTO> datasets = dataModel.getDatasets().stream()
                .filter(ds -> ds instanceof TableDataset)
                .map(ds -> {
                    TableDataset tds = (TableDataset) ds;
                    List<DatasetFieldDTO> fields = tds.getFields().stream()
                            .map(f -> new DatasetFieldDTO(
                                    f.getName(), f.getAlias(), f.getDataType().name(), f.isPrimaryKey()))
                            .toList();
                    return new DatasetInfoDTO(tds.getId(), tds.getAlias(), fields);
                })
                .toList();

        List<Relationship> rels = dataModel.getRelationships();
        List<RelationshipDTO> relationships = (rels == null ? List.<Relationship>of() : rels).stream()
                .map(r -> new RelationshipDTO(
                        new FieldRefDTO(r.getLeft().datasetId(), r.getLeft().field()),
                        new FieldRefDTO(r.getRight().datasetId(), r.getRight().field()),
                        r.getJoinType() != null ? r.getJoinType().name() : "INNER"))
                .toList();

        return SingleResponse.of(new DataModelDTO(datasets, relationships));
    }

    // ============================================================
    // DTO → 领域对象转换
    // ============================================================

    private List<CellBinding> convertBindings(List<BindingDTO> dtos) {
        if (dtos == null) return List.of();
        List<CellBinding> result = new ArrayList<>();
        for (BindingDTO dto : dtos) {
            result.add(CellBinding.builder()
                    .cell(parseCellKey(dto.cellKey()))
                    .value(convertValue(dto.value()))
                    .expansion(dto.expansion() != null ? Expansion.valueOf(dto.expansion()) : null)
                    .expandMode(dto.expandMode() != null ? ExpandMode.valueOf(dto.expandMode()) : null)
                    .mergeRepeated(dto.mergeRepeated())
                    .parentCell(dto.parentCell() != null ? parseCellKey(dto.parentCell()) : null)
                    .conditions(convertConditions(dto.conditions()))
                    .build());
        }
        return result;
    }

    private Value convertValue(ValueDTO dto) {
        if (dto == null) return new Value.Literal(null);
        return switch (dto.type()) {
            case "FieldValue" -> {
                String[] parts = dto.payload().split("\\.", 2);
                yield new Value.FieldValue(new FieldRef(parts[0], parts[1]));
            }
            case "Literal" -> new Value.Literal(dto.payload());
            case "NameRef" -> new Value.NameRef(dto.payload());
            case "ParamValue" -> new Value.ParamValue(dto.payload());
            case "LoopFieldValue" -> {
                String[] parts = dto.payload().split("\\.", 2);
                yield new Value.LoopFieldValue(parts[0], parts[1]);
            }
            case "Aggregate" -> new Value.Aggregate(
                    Aggregation.valueOf(dto.aggregation()),
                    convertValue(dto.operand()));
            case "FunctionCall" -> new Value.FunctionCall(
                    dto.funcName(),
                    dto.args() != null ? dto.args().stream().map(this::convertValue).toList() : List.of());
            case "Template" -> {
                List<Value.Template.Part> parts = new ArrayList<>();
                if (dto.parts() != null) {
                    for (PartDTO p : dto.parts()) {
                        if ("text".equals(p.kind())) {
                            parts.add(new Value.Template.Text(p.text()));
                        } else {
                            parts.add(new Value.Template.Hole(convertValue(p.value())));
                        }
                    }
                }
                yield new Value.Template(parts);
            }
            default -> new Value.Literal(dto.payload());
        };
    }

    private List<Condition> convertConditions(List<ConditionDTO> dtos) {
        if (dtos == null) return List.of();
        List<Condition> result = new ArrayList<>();
        for (ConditionDTO dto : dtos) {
            result.add(Condition.builder()
                    .left(convertValue(dto.left()))
                    .operator(CompareOperator.valueOf(dto.operator()))
                    .right(dto.right() != null ? convertValue(dto.right()) : null)
                    .build());
        }
        return result;
    }

    private List<LoopBlock> convertLoops(List<LoopBlockDTO> dtos) {
        if (dtos == null) return List.of();
        List<LoopBlock> result = new ArrayList<>();
        for (LoopBlockDTO dto : dtos) {
            Query query = Query.builder()
                    .datasetId(dto.source().datasetId())
                    .filters(convertConditions(dto.source().filters()))
                    .groupBy(dto.source().groupBy() != null ? dto.source().groupBy() : List.of())
                    .orderBy(dto.source().orderBy() != null ? dto.source().orderBy() : List.of())
                    .build();
            result.add(LoopBlock.builder()
                    .id(dto.id())
                    .label(dto.label())
                    .start(new CellRef(dto.sheetId(), dto.startRow(), dto.startColumn()))
                    .end(new CellRef(dto.sheetId(), dto.endRow(), dto.endColumn()))
                    .source(query)
                    .build());
        }
        return result;
    }

    private List<SummaryRow> convertSummaries(List<SummaryRowDTO> dtos) {
        if (dtos == null) return List.of();
        List<SummaryRow> result = new ArrayList<>();
        for (SummaryRowDTO dto : dtos) {
            FieldRef groupBy = dto.groupBy() != null
                    ? new FieldRef(dto.groupBy().datasetId(), dto.groupBy().field())
                    : null;
            List<SummaryCell> cells = new ArrayList<>();
            if (dto.cells() != null) {
                for (SummaryCellDTO c : dto.cells()) {
                    if ("label".equals(c.kind())) {
                        cells.add(SummaryCell.label(c.column(), c.payload()));
                    } else {
                        String[] parts = c.payload().split("\\.", 2);
                        cells.add(SummaryCell.agg(c.column(),
                                new FieldRef(parts[0], parts[1]),
                                Aggregation.valueOf(c.aggregation())));
                    }
                }
            }
            result.add(SummaryRow.builder().groupBy(groupBy).cells(cells).build());
        }
        return result;
    }

    private static CellRef parseCellKey(String key) {
        String[] parts = key.split(":");
        return new CellRef(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    // ============================================================
    // DTO Records
    // ============================================================

    public record RenderRequest(
            List<BindingDTO> cellBindings,
            List<LoopBlockDTO> loopBlocks,
            List<SummaryRowDTO> summaries,
            Map<String, Object> params,
            Workbook template) {
    }

    public record BindingDTO(
            String cellKey,
            ValueDTO value,
            String expansion,
            String expandMode,
            boolean mergeRepeated,
            String parentCell,
            List<ConditionDTO> conditions,
            String preview) {
    }

    public record ValueDTO(
            String type,
            String payload,
            String aggregation,
            ValueDTO operand,
            String funcName,
            List<ValueDTO> args,
            List<PartDTO> parts) {
    }

    public record PartDTO(String kind, String text, ValueDTO value) {
    }

    public record ConditionDTO(String id, ValueDTO left, String operator, ValueDTO right) {
    }

    public record LoopBlockDTO(
            String id, String label, String sheetId,
            int startRow, int startColumn, int endRow, int endColumn,
            SourceDTO source) {
    }

    public record SourceDTO(
            String datasetId,
            List<ConditionDTO> filters,
            List<String> groupBy,
            List<String> orderBy) {
    }

    public record SummaryRowDTO(FieldRefDTO groupBy, List<SummaryCellDTO> cells) {
    }

    public record FieldRefDTO(String datasetId, String field) {
    }

    public record SummaryCellDTO(int column, String kind, String payload, String aggregation, String preview) {
    }

    // ============================================================
    // 数据模型 DTO（GET /datamodel）
    // ============================================================

    public record DataModelDTO(List<DatasetInfoDTO> datasets, List<RelationshipDTO> relationships) {
    }

    public record DatasetInfoDTO(String id, String alias, List<DatasetFieldDTO> fields) {
    }

    public record DatasetFieldDTO(String name, String alias, String dataType, boolean primaryKey) {
    }

    public record RelationshipDTO(FieldRefDTO left, FieldRefDTO right, String joinType) {
    }
}
