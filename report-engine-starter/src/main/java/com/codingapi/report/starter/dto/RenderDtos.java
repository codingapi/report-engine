package com.codingapi.report.starter.dto;

import com.codingapi.report.config.dto.ConfigDtos.BindingDTO;
import com.codingapi.report.config.dto.ConfigDtos.LoopBlockDTO;
import com.codingapi.report.config.dto.ConfigDtos.SummaryRowDTO;
import com.codingapi.report.excel.pojo.Workbook;
import java.util.List;
import java.util.Map;

/**
 * 渲染请求/响应的 DTO 契约（前端 JSON ↔ 这些 DTO ↔ framework 领域对象）。
 *
 * <p>单元格绑定 / 循环块 / 汇总行的 DTO record 已上提到 framework {@code
 * com.codingapi.report.config.dto.ConfigDtos}（同时作为 {@code ReportConfig} 实体的持久化契约）， 本类保留渲染请求 {@link
 * RenderRequest} 与渲染响应（预览结果 / 反查请求与结果）。
 */
public final class RenderDtos {

    private RenderDtos() {}

    public record RenderRequest(
            String dataModelId,
            List<BindingDTO> cellBindings,
            List<LoopBlockDTO> loopBlocks,
            List<SummaryRowDTO> summaries,
            Map<String, Object> params,
            Workbook template) {}

    /** 预览响应：工作簿 + 反查格坐标列表（"row:col" 格式） */
    public record PreviewResult(Workbook workbook, List<String> drillable) {}

    /** 反查请求：渲染配置 + 目标格坐标 */
    public record DrillRequest(RenderRequest request, int row, int col) {}

    /** 反查结果：数据集 id/别名 + 字段列表 + 明细行（本期全量返回，不分页） */
    public record DrillResult(
            String datasetId,
            String alias,
            List<FieldInfo> fields,
            List<Map<String, Object>> rows) {}

    /** 字段信息：name + alias */
    public record FieldInfo(String name, String alias) {}
}
