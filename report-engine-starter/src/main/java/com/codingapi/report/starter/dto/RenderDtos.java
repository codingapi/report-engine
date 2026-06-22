package com.codingapi.report.starter.dto;

import com.codingapi.report.config.dto.ConfigDtos.BindingDTO;
import com.codingapi.report.config.dto.ConfigDtos.LoopBlockDTO;
import com.codingapi.report.config.dto.ConfigDtos.SummaryRowDTO;
import com.codingapi.report.excel.pojo.Workbook;

import java.util.List;
import java.util.Map;

/**
 * 渲染请求的 DTO 契约（前端 JSON → 这些 DTO → framework 领域对象）。
 * <p>
 * 单元格绑定 / 循环块 / 汇总行的 DTO record 已上提到 framework
 * {@code com.codingapi.report.config.dto.ConfigDtos}（同时作为 {@code ReportConfig} 实体的持久化契约），
 * 本类仅保留渲染请求 {@link RenderRequest}（params 为运行时参数值 Map，与实体的 params 定义不同）。
 */
public final class RenderDtos {

    private RenderDtos() {
    }

    public record RenderRequest(
            List<BindingDTO> cellBindings,
            List<LoopBlockDTO> loopBlocks,
            List<SummaryRowDTO> summaries,
            Map<String, Object> params,
            Workbook template) {
    }
}
