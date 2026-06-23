package com.codingapi.report.config;

import com.codingapi.report.config.dto.ConfigDtos.BindingDTO;
import com.codingapi.report.config.dto.ConfigDtos.LoopBlockDTO;
import com.codingapi.report.config.dto.ConfigDtos.SummaryRowDTO;
import com.codingapi.report.config.dto.ReportParam;
import com.codingapi.report.excel.pojo.Workbook;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 报表配置实体（持久化契约）：强类型 POJO，替代原 {@code Map<String,Object>} 存取。
 * <p>
 * 字段包含报表元数据（id/name/dataModelId/createTime/updateTime）与配置内容
 * （cellBindings/loopBlocks/summaries/params/template）。配置内容引用 {@link ConfigDtos} 的 DTO record
 * （Jackson 可序列化，不依赖无注解的 {@code Value} sealed interface），便于落库 JSON。
 * <p>
 * {@code dataModel} 为响应富化字段：仅 {@code GET /api/report/configs/{id}} 返回时由 starter 填充，
 * 不参与持久化（{@link JsonInclude.Include#NON_NULL} 省略空值）。
 */
@Data
public class ReportConfig {

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
