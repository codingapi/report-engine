package com.codingapi.report.dto.report;


import java.util.List;

/**
 * 汇总持久化契约。{@code axis} 为 "VERTICAL"/"HORIZONTAL"，null 视为 VERTICAL（向后兼容）。 坐标按轴转置：{@code mainPos}
 * 主轴声明位置（纵向=行/横向=列）、{@code crossFrom/crossTo} 交叉区间（纵向=列/横向=行）。
 */
public record SummaryRowDTO(
        String id,
        String axis,
        FieldRefDTO groupBy,
        int crossFrom,
        int crossTo,
        List<SummaryCellDTO> cells,
        Integer mainPos) {}
