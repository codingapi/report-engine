package com.example.report.controller;

import com.codingapi.springboot.framework.dto.response.MultiResponse;
import com.codingapi.springboot.framework.dto.response.SingleResponse;
import com.example.report.repository.ReportRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 报表配置的保存 / 加载 / 列表。
 * <p>
 * 配置以原样 JSON 存储（name/cellBindings/loopBlocks/summaries/params/template），
 * 打开报表时整体恢复前端状态。
 * </p>
 */
@RestController
@RequestMapping("/api/report")
public class ReportConfigController {

    private final ReportRepository repository;

    public ReportConfigController(ReportRepository repository) {
        this.repository = repository;
    }

    /** 保存报表配置，返回报表 id。 */
    @PostMapping("/configs")
    public SingleResponse<String> save(@RequestBody Map<String, Object> config) {
        return SingleResponse.of(repository.save(config));
    }

    /** 加载指定报表的完整配置。 */
    @GetMapping("/configs/{id}")
    public SingleResponse<Map<String, Object>> get(@PathVariable String id) {
        return SingleResponse.of(repository.find(id));
    }

    /** 报表列表（仅 id + name，供"打开"选择）。 */
    @GetMapping("/configs")
    public MultiResponse<ReportBrief> list() {
        List<ReportBrief> briefs = repository.all().stream()
                .map(c -> new ReportBrief(
                        String.valueOf(c.get("id")),
                        c.get("name") != null ? String.valueOf(c.get("name")) : "未命名报表"))
                .toList();
        return MultiResponse.of(briefs);
    }

    public record ReportBrief(String id, String name) {
    }
}
