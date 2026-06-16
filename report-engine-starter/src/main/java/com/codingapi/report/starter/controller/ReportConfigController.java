package com.codingapi.report.starter.controller;

import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.repository.ReportRepository;
import com.codingapi.report.starter.converter.DataModelDtoAssembler;
import com.codingapi.springboot.framework.dto.response.MultiResponse;
import com.codingapi.springboot.framework.dto.response.SingleResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表配置的保存 / 加载 / 列表。
 * <p>
 * 配置以原样 JSON 存储（name/cellBindings/loopBlocks/summaries/params/template），
 * 打开报表时整体恢复前端状态。加载时附带数据模型信息（datasets + relationships）。
 * <p>
 * 存储交给 {@link ReportRepository}（使用方提供实现，starter 提供默认内存实现）。
 * <p>
 * 示例报表端点由使用方（如 example）单独实现——示例是应用级能力，不下沉到 starter。
 * </p>
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
    public SingleResponse<String> save(@RequestBody Map<String, Object> config) {
        return SingleResponse.of(repository.save(config));
    }

    /** 加载指定报表的完整配置（附带数据模型信息）。 */
    @GetMapping("/configs/{id}")
    public SingleResponse<Map<String, Object>> get(@PathVariable String id) {
        Map<String, Object> config = repository.find(id);
        if (config == null) {
            return SingleResponse.of(null);
        }
        Map<String, Object> enriched = new HashMap<>(config);
        enriched.put("dataModel", DataModelDtoAssembler.assemble(dataModel));
        return SingleResponse.of(enriched);
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
