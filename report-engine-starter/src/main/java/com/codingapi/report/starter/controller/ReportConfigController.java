package com.codingapi.report.starter.controller;

import com.codingapi.report.core.Report;
import com.codingapi.report.dto.report.ReportDTO;
import com.codingapi.report.repository.PageResult;
import com.codingapi.report.starter.service.ReportConfigService;
import com.codingapi.springboot.framework.dto.response.MultiResponse;
import com.codingapi.springboot.framework.dto.response.SingleResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @PostMapping("/configs/{id}/delete")
    public SingleResponse<Void> delete(@PathVariable String id) {
        reportConfigService.delete(id);
        return SingleResponse.of(null);
    }

    @GetMapping("/configs")
    public MultiResponse<ReportBrief> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 显式 @RequestParam 绑定分页参数，避免 SearchRequest 的 CurrentPageOffsetContext
        // 把 current 规范化（该框架偏好 offset 分页）导致 ?current=N 被吞、切页数据不变。
        PageResult<Report> result = reportConfigService.page(current, pageSize);
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
