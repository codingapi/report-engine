package com.codingapi.report.starter.controller;

import com.codingapi.report.config.dto.ReportDTO;
import com.codingapi.report.core.Report;
import com.codingapi.report.repository.PageResult;
import com.codingapi.report.starter.service.ReportConfigService;
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
 * <p>仅做 HTTP 编排，业务（CRUD + 富化）下沉 {@link ReportConfigService}。 存储交给 {@link
 * com.codingapi.report.repository.ReportRepository}（使用方提供实现）。
 */
@RestController
@RequestMapping("/api/report")
@ConditionalOnClass(RestController.class)
public class ReportConfigController {

    private final ReportConfigService reportConfigService;

    public ReportConfigController(ReportConfigService reportConfigService) {
        this.reportConfigService = reportConfigService;
    }

    @PostMapping("/configs")
    public SingleResponse<String> save(@RequestBody ReportDTO config) {
        return SingleResponse.of(reportConfigService.save(config));
    }

    @GetMapping("/configs/{id}")
    public SingleResponse<ReportDTO> get(@PathVariable String id) {
        return SingleResponse.of(reportConfigService.get(id));
    }

    @DeleteMapping("/configs/{id}")
    public SingleResponse<Void> delete(@PathVariable String id) {
        reportConfigService.delete(id);
        return SingleResponse.of(null);
    }

    @GetMapping("/configs")
    public MultiResponse<ReportBrief> list(SearchRequest searchRequest) {
        PageResult<Report> result =
                reportConfigService.page(searchRequest.getCurrent(), searchRequest.getPageSize());
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
