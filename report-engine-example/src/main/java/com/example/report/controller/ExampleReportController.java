package com.example.report.controller;

import com.codingapi.report.repository.ReportRepository;
import com.codingapi.springboot.framework.dto.response.MultiResponse;
import com.example.report.config.ReportTemplateSeeder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 示例报表 API（应用级能力）。
 * <p>
 * 示例报表是 example 模块的应用级数据，不下沉到 starter。本 Controller 通过
 * {@link ReportTemplateSeeder} 获取预存示例的有序 id，再通过 {@link ReportRepository}
 * 拉取配置名称供前端列表。
 */
@RestController
@RequestMapping("/api/report")
public class ExampleReportController {

    private final ReportRepository repository;
    private final ReportTemplateSeeder templateSeeder;

    public ExampleReportController(ReportRepository repository, ReportTemplateSeeder templateSeeder) {
        this.repository = repository;
        this.templateSeeder = templateSeeder;
    }

    /** 示例报表列表（预存的测试报表 id + name，供"快速打开"）。 */
    @GetMapping("/configs/examples")
    public MultiResponse<ReportBrief> examples() {
        List<ReportBrief> briefs = templateSeeder.getExampleIds().stream()
                .map(id -> {
                    Map<String, Object> config = repository.find(id);
                    String name = config != null && config.get("name") != null
                            ? String.valueOf(config.get("name")) : "示例报表";
                    return new ReportBrief(id, name);
                })
                .toList();
        return MultiResponse.of(briefs);
    }

    public record ReportBrief(String id, String name) {
    }
}
