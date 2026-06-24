package com.codingapi.report.starter.controller;

import com.codingapi.report.config.ReportConfig;
import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.repository.PageQuery;
import com.codingapi.report.repository.PageResult;
import com.codingapi.report.repository.ReportRepository;
import com.codingapi.report.starter.converter.DataModelDtoAssembler;
import com.codingapi.springboot.framework.dto.request.SearchRequest;
import com.codingapi.springboot.framework.dto.response.MultiResponse;
import com.codingapi.springboot.framework.dto.response.SingleResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报表配置的保存 / 加载 / 列表 / 删除。
 *
 * <p>配置以强类型 {@link ReportConfig} 实体存取（name/cellBindings/loopBlocks/summaries/params/template +
 * 时间戳）， 打开报表时整体恢复前端状态。加载时附带数据模型信息（datasets + relationships）。
 *
 * <p>存储交给 {@link ReportRepository}（使用方提供实现）。
 */
@RestController
@RequestMapping("/api/report")
@ConditionalOnClass(RestController.class)
public class ReportConfigController {

    private final ReportRepository repository;
    private final DataModel dataModel;

    public ReportConfigController(ReportRepository repository, DataModel dataModel) {
        this.repository = repository;
        this.dataModel = dataModel;
    }

    /** 保存报表配置，返回报表 id。 */
    @PostMapping("/configs")
    public SingleResponse<String> save(@RequestBody ReportConfig config) {
        return SingleResponse.of(repository.save(config));
    }

    /** 加载指定报表的完整配置（附带数据模型信息）。 */
    @GetMapping("/configs/{id}")
    public SingleResponse<ReportConfig> get(@PathVariable String id) {
        ReportConfig config = repository.find(id);
        if (config == null) {
            return SingleResponse.of(null);
        }
        config.setDataModel(DataModelDtoAssembler.assemble(dataModel));
        return SingleResponse.of(config);
    }

    /** 删除指定报表配置。 */
    @DeleteMapping("/configs/{id}")
    public SingleResponse<Void> delete(@PathVariable String id) {
        repository.delete(id);
        return SingleResponse.of(null);
    }

    /** 报表列表（id + name + dataModelId + 时间戳），按 SearchRequest 分页。 */
    @GetMapping("/configs")
    public MultiResponse<ReportBrief> list(SearchRequest searchRequest) {
        // Spring 入参 → framework 分页类型（接口本身不依赖 Spring）
        PageQuery query = new PageQuery(searchRequest.getCurrent(), searchRequest.getPageSize());
        PageResult<ReportConfig> result = repository.page(query);
        List<ReportBrief> briefs =
                result.content().stream()
                        .map(
                                r ->
                                        new ReportBrief(
                                                r.getId(),
                                                r.getName() != null ? r.getName() : "未命名报表",
                                                r.getDataModelId(),
                                                r.getCreateTime(),
                                                r.getUpdateTime()))
                        .toList();
        return MultiResponse.of(briefs, result.total());
    }

    public record ReportBrief(
            String id, String name, String dataModelId, long createTime, long updateTime) {}
}
