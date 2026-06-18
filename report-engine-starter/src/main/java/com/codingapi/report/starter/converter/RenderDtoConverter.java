package com.codingapi.report.starter.converter;

import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.data.dataset.Query;
import com.codingapi.report.operator.condition.CompareOperator;
import com.codingapi.report.operator.condition.Condition;
import com.codingapi.report.expression.Value;
import com.codingapi.report.render.grid.CellBinding;
import com.codingapi.report.render.grid.CellRef;
import com.codingapi.report.render.grid.ExpandMode;
import com.codingapi.report.render.grid.Expansion;
import com.codingapi.report.render.grid.LoopBlock;
import com.codingapi.report.render.grid.SummaryCell;
import com.codingapi.report.render.grid.SummaryRow;
import com.codingapi.report.starter.dto.RenderDtos.BindingDTO;
import com.codingapi.report.starter.dto.RenderDtos.ConditionDTO;
import com.codingapi.report.starter.dto.RenderDtos.LoopBlockDTO;
import com.codingapi.report.starter.dto.RenderDtos.PartDTO;
import com.codingapi.report.starter.dto.RenderDtos.SummaryCellDTO;
import com.codingapi.report.starter.dto.RenderDtos.SummaryRowDTO;
import com.codingapi.report.starter.dto.RenderDtos.ValueDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * 渲染请求 DTO → framework 领域对象的转换器。
 * <p>
 * 因 {@code Value} 等 sealed interface 无 Jackson 多态注解，前端 JSON 经 {@link com.codingapi.report.starter.dto.RenderDtos}
 * 承接后由此处统一转换。所有方法无状态（static）。
 */
public final class RenderDtoConverter {

    private RenderDtoConverter() {
    }

    public static List<CellBinding> convertBindings(List<BindingDTO> dtos) {
        if (dtos == null) return List.of();
        List<CellBinding> result = new ArrayList<>();
        for (BindingDTO dto : dtos) {
            result.add(CellBinding.builder()
                    .cell(CellRef.parse(dto.cellKey()))
                    .value(convertValue(dto.value()))
                    .expansion(dto.expansion() != null ? Expansion.valueOf(dto.expansion()) : null)
                    .expandMode(dto.expandMode() != null ? ExpandMode.valueOf(dto.expandMode()) : null)
                    .mergeRepeated(dto.mergeRepeated())
                    .parentCell(dto.parentCell() != null ? CellRef.parse(dto.parentCell()) : null)
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
            case "Aggregate" -> new Value.Aggregate(
                    dto.aggregation(),
                    convertValue(dto.operand()));
            case "FunctionCall" -> new Value.FunctionCall(
                    dto.funcName(),
                    dto.args() != null ? dto.args().stream().map(RenderDtoConverter::convertValue).toList() : List.of());
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
            result.add(Condition.builder()
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

    public static List<SummaryRow> convertSummaries(List<SummaryRowDTO> dtos) {
        if (dtos == null) return List.of();
        List<SummaryRow> result = new ArrayList<>();
        for (SummaryRowDTO dto : dtos) {
            FieldRef groupBy = dto.groupBy() != null
                    ? new FieldRef(dto.groupBy().datasetId(), dto.groupBy().field())
                    : null;
            List<SummaryCell> cells = new ArrayList<>();
            if (dto.cells() != null) {
                for (SummaryCellDTO c : dto.cells()) {
                    if (c.value() != null) {
                        // 新格式：直接使用 ValueDTO + 反查配置
                        cells.add(new SummaryCell(c.column(), convertValue(c.value()), c.drillEnabled(), c.drillView()));
                    } else if ("label".equals(c.kind())) {
                        // 旧格式兼容
                        cells.add(SummaryCell.label(c.column(), c.payload()));
                    } else {
                        // 旧格式 agg 兼容
                        String[] parts = c.payload().split("\\.", 2);
                        cells.add(SummaryCell.agg(c.column(),
                                new FieldRef(parts[0], parts[1]),
                                c.aggregation()));
                    }
                }
            }
            result.add(SummaryRow.builder().groupBy(groupBy)
                    .fromColumn(dto.fromColumn()).toColumn(dto.toColumn())
                    .cells(cells).row(dto.row()).build());
        }
        return result;
    }

}
