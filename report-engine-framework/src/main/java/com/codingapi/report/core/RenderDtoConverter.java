package com.codingapi.report.core;

import com.codingapi.report.config.dto.ConfigDtos.BindingDTO;
import com.codingapi.report.config.dto.ConfigDtos.ConditionDTO;
import com.codingapi.report.config.dto.ConfigDtos.LoopBlockDTO;
import com.codingapi.report.config.dto.ConfigDtos.PartDTO;
import com.codingapi.report.config.dto.ConfigDtos.SummaryCellDTO;
import com.codingapi.report.config.dto.ConfigDtos.SummaryRowDTO;
import com.codingapi.report.config.dto.ConfigDtos.FieldRefDTO;
import com.codingapi.report.config.dto.ConfigDtos.SourceDTO;
import com.codingapi.report.config.dto.ConfigDtos.ValueDTO;
import com.codingapi.report.config.dto.ReportDTO;
import com.codingapi.report.config.dto.ReportParam;
import com.codingapi.report.data.dataset.DataType;
import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.param.ParamSource;
import com.codingapi.report.param.Parameter;
import com.codingapi.report.data.dataset.Query;
import com.codingapi.report.expression.Value;
import com.codingapi.report.operator.condition.CompareOperator;
import com.codingapi.report.operator.condition.Condition;
import com.codingapi.report.core.grid.Axis;
import com.codingapi.report.core.grid.CellBinding;
import com.codingapi.report.core.grid.CellRef;
import com.codingapi.report.core.grid.ExpandMode;
import com.codingapi.report.core.grid.Expansion;
import com.codingapi.report.core.grid.LoopBlock;
import com.codingapi.report.core.grid.SummaryCell;
import com.codingapi.report.core.grid.SummaryRow;
import java.util.ArrayList;
import java.util.List;

/**
 * 渲染请求 DTO → framework 领域对象的转换器。
 *
 * <p>因 {@code Value} 等 sealed interface 无 Jackson 多态注解，前端 JSON 经 {@link
 * com.codingapi.report.starter.dto.RenderDtos} 承接后由此处统一转换。所有方法无状态（static）。
 */
public final class RenderDtoConverter {

    private RenderDtoConverter() {}

    public static List<CellBinding> convertBindings(List<BindingDTO> dtos) {
        if (dtos == null) return List.of();
        List<CellBinding> result = new ArrayList<>();
        for (BindingDTO dto : dtos) {
            result.add(
                    CellBinding.builder()
                            .cell(CellRef.parse(dto.cellKey()))
                            .value(convertValue(dto.value()))
                            .expansion(
                                    dto.expansion() != null
                                            ? Expansion.valueOf(dto.expansion())
                                            : null)
                            .expandMode(
                                    dto.expandMode() != null
                                            ? ExpandMode.valueOf(dto.expandMode())
                                            : null)
                            .mergeRepeated(dto.mergeRepeated())
                            .parentCell(
                                    dto.parentCell() != null
                                            ? CellRef.parse(dto.parentCell())
                                            : null)
                            .conditions(convertConditions(dto.conditions()))
                            .independent(dto.independent())
                            .drillEnabled(dto.drillEnabled())
                            .drillView(dto.drillView())
                            .build());
        }
        return result;
    }

    public static Value convertValue(ValueDTO dto) {
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
            case "Aggregate" -> new Value.Aggregate(dto.aggregation(), convertValue(dto.operand()));
            case "FunctionCall" ->
                    new Value.FunctionCall(
                            dto.funcName(),
                            dto.args() != null
                                    ? dto.args().stream()
                                            .map(RenderDtoConverter::convertValue)
                                            .toList()
                                    : List.of());
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

    public static List<Condition> convertConditions(List<ConditionDTO> dtos) {
        if (dtos == null) return List.of();
        List<Condition> result = new ArrayList<>();
        for (ConditionDTO dto : dtos) {
            result.add(
                    Condition.builder()
                            .left(convertValue(dto.left()))
                            .operator(CompareOperator.valueOf(dto.operator()))
                            .right(dto.right() != null ? convertValue(dto.right()) : null)
                            .build());
        }
        return result;
    }

    public static List<LoopBlock> convertLoops(List<LoopBlockDTO> dtos) {
        if (dtos == null) return List.of();
        List<LoopBlock> result = new ArrayList<>();
        for (LoopBlockDTO dto : dtos) {
            Query query =
                    Query.builder()
                            .datasetId(dto.source().datasetId())
                            .filters(convertConditions(dto.source().filters()))
                            .groupBy(
                                    dto.source().groupBy() != null
                                            ? dto.source().groupBy()
                                            : List.of())
                            .orderBy(
                                    dto.source().orderBy() != null
                                            ? dto.source().orderBy()
                                            : List.of())
                            .build();
            result.add(
                    LoopBlock.builder()
                            .id(dto.id())
                            .label(dto.label())
                            .start(new CellRef(dto.sheetId(), dto.startRow(), dto.startColumn()))
                            .end(new CellRef(dto.sheetId(), dto.endRow(), dto.endColumn()))
                            .source(query)
                            .build());
        }
        return result;
    }

    public static List<SummaryRow> convertSummaries(List<SummaryRowDTO> dtos) {
        if (dtos == null) return List.of();
        List<SummaryRow> result = new ArrayList<>();
        for (SummaryRowDTO dto : dtos) {
            FieldRef groupBy =
                    dto.groupBy() != null
                            ? new FieldRef(dto.groupBy().datasetId(), dto.groupBy().field())
                            : null;
            List<SummaryCell> cells = new ArrayList<>();
            if (dto.cells() != null) {
                for (SummaryCellDTO c : dto.cells()) {
                    if (c.value() != null) {
                        // 新格式：直接使用 ValueDTO + 反查配置
                        cells.add(
                                new SummaryCell(
                                        c.crossPos(),
                                        convertValue(c.value()),
                                        c.drillEnabled(),
                                        c.drillView()));
                    } else if ("label".equals(c.kind())) {
                        // 旧格式兼容
                        cells.add(SummaryCell.label(c.crossPos(), c.payload()));
                    } else {
                        // 旧格式 agg 兼容
                        String[] parts = c.payload().split("\\.", 2);
                        cells.add(
                                SummaryCell.agg(
                                        c.crossPos(),
                                        new FieldRef(parts[0], parts[1]),
                                        c.aggregation()));
                    }
                }
            }
            Axis axis = "HORIZONTAL".equals(dto.axis()) ? Axis.HORIZONTAL : Axis.VERTICAL;
            result.add(
                    SummaryRow.builder()
                            .axis(axis)
                            .groupBy(groupBy)
                            .crossFrom(dto.crossFrom())
                            .crossTo(dto.crossTo())
                            .cells(cells)
                            .mainPos(dto.mainPos())
                            .build());
        }
        return result;
    }

    public static List<Parameter> toParameters(List<ReportParam> params) {
        if (params == null) return List.of();
        List<Parameter> out = new ArrayList<>();
        for (ReportParam p : params) {
            out.add(
                    Parameter.builder()
                            .name(p.getName())
                            .alias(p.getAlias())
                            .dataType(
                                    p.getDataType() != null
                                            ? DataType.valueOf(p.getDataType())
                                            : DataType.STRING)
                            .source(new ParamSource.External(false, p.getDefaultValue()))
                            .build());
        }
        return out;
    }

    // ============================================================
    // 领域 → DTO（出站）。前端展示用字段（preview/param.id/summary.id）不反向产出。
    // ============================================================

    public static ReportDTO toDTO(Report r) {
        if (r == null) return null;
        ReportDTO dto = new ReportDTO();
        dto.setId(r.getId());
        dto.setName(r.getName());
        dto.setDataModelId(r.getDataModelId());
        dto.setCreateTime(r.getCreateTime());
        dto.setUpdateTime(r.getUpdateTime());
        dto.setCellBindings(toBindingDtos(r.getCellBindings()));
        dto.setLoopBlocks(toLoopDtos(r.getLoopBlocks()));
        dto.setSummaries(toSummaryDtos(r.getSummaries()));
        dto.setParams(toParamDtos(r.getParameters()));
        dto.setTemplate(r.getTemplate());
        return dto;
    }

    public static Report fromDTO(ReportDTO dto) {
        if (dto == null) return null;
        return Report.builder()
                .id(dto.getId())
                .name(dto.getName())
                .dataModelId(dto.getDataModelId())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .cellBindings(convertBindings(dto.getCellBindings()))
                .loopBlocks(convertLoops(dto.getLoopBlocks()))
                .summaries(convertSummaries(dto.getSummaries()))
                .parameters(toParameters(dto.getParams()))
                .template(dto.getTemplate())
                .build();
    }

    private static String key(CellRef c) {
        return c == null ? null : c.sheetId() + ":" + c.row() + ":" + c.column();
    }

    public static List<BindingDTO> toBindingDtos(List<CellBinding> bindings) {
        if (bindings == null) return List.of();
        List<BindingDTO> out = new ArrayList<>();
        for (CellBinding b : bindings) {
            out.add(
                    new BindingDTO(
                            key(b.getCell()),
                            toValueDto(b.getValue()),
                            b.getExpansion() != null ? b.getExpansion().name() : null,
                            b.getExpandMode() != null ? b.getExpandMode().name() : null,
                            b.isMergeRepeated(),
                            b.getParentCell() != null ? key(b.getParentCell()) : null,
                            toConditionDtos(b.getConditions()),
                            b.isIndependent(),
                            null,
                            b.isDrillEnabled(),
                            b.getDrillView()));
        }
        return out;
    }

    public static ValueDTO toValueDto(Value v) {
        if (v == null) return null;
        if (v instanceof Value.Literal l) {
            return new ValueDTO(
                    "Literal",
                    l.value() != null ? l.value().toString() : null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }
        if (v instanceof Value.FieldValue f) {
            return new ValueDTO(
                    "FieldValue",
                    f.ref().datasetId() + "." + f.ref().field(),
                    null,
                    null,
                    null,
                    null,
                    null);
        }
        if (v instanceof Value.ParamValue p) {
            return new ValueDTO("ParamValue", p.name(), null, null, null, null, null);
        }
        if (v instanceof Value.LoopFieldValue lf) {
            return new ValueDTO(
                    "LoopFieldValue",
                    lf.loopBlockId() + "." + lf.field(),
                    null,
                    null,
                    null,
                    null,
                    null);
        }
        if (v instanceof Value.NameRef n) {
            return new ValueDTO("NameRef", n.name(), null, null, null, null, null);
        }
        if (v instanceof Value.Aggregate a) {
            return new ValueDTO(
                    "Aggregate", null, a.aggregation(), toValueDto(a.operand()), null, null, null);
        }
        if (v instanceof Value.FunctionCall fc) {
            return new ValueDTO(
                    "FunctionCall",
                    null,
                    null,
                    null,
                    fc.name(),
                    fc.args() != null
                            ? fc.args().stream().map(RenderDtoConverter::toValueDto).toList()
                            : List.of(),
                    null);
        }
        if (v instanceof Value.Template t) {
            List<PartDTO> parts = new ArrayList<>();
            if (t.parts() != null) {
                for (Value.Template.Part p : t.parts()) {
                    if (p instanceof Value.Template.Text txt) {
                        parts.add(new PartDTO("text", txt.text(), null));
                    } else if (p instanceof Value.Template.Hole h) {
                        parts.add(new PartDTO("hole", null, toValueDto(h.value())));
                    }
                }
            }
            return new ValueDTO("Template", null, null, null, null, null, parts);
        }
        throw new IllegalStateException("未知 Value 类型: " + v.getClass().getName());
    }

    public static List<ConditionDTO> toConditionDtos(List<Condition> conditions) {
        if (conditions == null) return List.of();
        List<ConditionDTO> out = new ArrayList<>();
        for (Condition c : conditions) {
            out.add(
                    new ConditionDTO(
                            null,
                            toValueDto(c.getLeft()),
                            c.getOperator() != null ? c.getOperator().name() : null,
                            c.getRight() != null ? toValueDto(c.getRight()) : null));
        }
        return out;
    }

    public static List<LoopBlockDTO> toLoopDtos(List<LoopBlock> loops) {
        if (loops == null) return List.of();
        List<LoopBlockDTO> out = new ArrayList<>();
        for (LoopBlock lb : loops) {
            Query q = lb.getSource();
            SourceDTO src =
                    q == null
                            ? null
                            : new SourceDTO(
                                    q.getDatasetId(),
                                    toConditionDtos(q.getFilters()),
                                    q.getGroupBy(),
                                    q.getOrderBy());
            CellRef s = lb.getStart();
            CellRef e = lb.getEnd();
            out.add(
                    new LoopBlockDTO(
                            lb.getId(),
                            lb.getLabel(),
                            s != null ? s.sheetId() : null,
                            s != null ? s.row() : 0,
                            s != null ? s.column() : 0,
                            e != null ? e.row() : 0,
                            e != null ? e.column() : 0,
                            src));
        }
        return out;
    }

    public static List<SummaryRowDTO> toSummaryDtos(List<SummaryRow> rows) {
        if (rows == null) return List.of();
        List<SummaryRowDTO> out = new ArrayList<>();
        for (SummaryRow sr : rows) {
            FieldRefDTO gb =
                    sr.getGroupBy() != null
                            ? new FieldRefDTO(sr.getGroupBy().datasetId(), sr.getGroupBy().field())
                            : null;
            List<SummaryCellDTO> cells = new ArrayList<>();
            if (sr.getCells() != null) {
                for (SummaryCell c : sr.getCells()) {
                    cells.add(
                            new SummaryCellDTO(
                                    c.getCrossPos(),
                                    toValueDto(c.getValue()),
                                    null,
                                    null,
                                    null,
                                    null,
                                    c.isDrillEnabled(),
                                    c.getDrillView()));
                }
            }
            out.add(
                    new SummaryRowDTO(
                            null,
                            sr.getAxis() != null ? sr.getAxis().name() : null,
                            gb,
                            sr.getCrossFrom(),
                            sr.getCrossTo(),
                            cells,
                            sr.getMainPos()));
        }
        return out;
    }

    public static List<ReportParam> toParamDtos(List<Parameter> params) {
        if (params == null) return List.of();
        List<ReportParam> out = new ArrayList<>();
        for (Parameter p : params) {
            ReportParam rp = new ReportParam();
            rp.setName(p.getName());
            rp.setAlias(p.getAlias());
            rp.setDataType(p.getDataType() != null ? p.getDataType().name() : null);
            if (p.getSource() instanceof ParamSource.External e && e.defaultValue() != null) {
                rp.setDefaultValue(e.defaultValue().toString());
            }
            out.add(rp);
        }
        return out;
    }
}
