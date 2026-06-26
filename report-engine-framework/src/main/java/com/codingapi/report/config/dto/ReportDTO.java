package com.codingapi.report.config.dto;

import com.codingapi.report.config.dto.ConfigDtos.BindingDTO;
import com.codingapi.report.config.dto.ConfigDtos.LoopBlockDTO;
import com.codingapi.report.config.dto.ConfigDtos.SummaryRowDTO;
import com.codingapi.report.excel.pojo.Workbook;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Data;

/**
 * 报表出入站契约（GET 返回 / POST 保存）。纯 DTO——前端 JSON ↔ 领域 {@code core.Report}（经 {@code
 * RenderDtoConverter.fromDTO/toDTO} 互转）。
 *
 * <p>{@code Value} 等 sealed interface 未加 Jackson 多态注解，故配置内容用 {@link ConfigDtos} 的 record 承接。 {@code
 * dataModel} 为响应富化字段：仅 {@code GET /api/report/configs/{id}} 返回时由 starter 填充，不持久化（{@link
 * JsonInclude.Include#NON_NULL} 省略空值）。
 *
 * <p>注意:前端展示用字段（{@code BindingDTO.preview}、{@code ReportParam.id} 等）不进领域对象、不持久化，由前端按需重建。
 */
@Data
public class ReportDTO {

    private String id;

    private String name;

    /** 引用的数据模型 id */
    private String dataModelId;

    /** 创建时间（epoch 毫秒） */
    private long createTime;

    /** 修改时间（epoch 毫秒） */
    private long updateTime;

    private List<BindingDTO> cellBindings;

    private List<LoopBlockDTO> loopBlocks;

    private List<SummaryRowDTO> summaries;

    private List<ReportParam> params;

    /** 模板工作簿快照（Jackson 友好的 Excel POJO） */
    private Workbook template;

    /** 响应富化字段：仅加载配置时填充，不持久化 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object dataModel;
}
