package com.example.report.controller;

import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.relation.Relationship;
import com.codingapi.springboot.framework.dto.response.MultiResponse;
import com.codingapi.springboot.framework.dto.response.SingleResponse;
import com.example.report.config.ReportTemplateSeeder;
import com.example.report.repository.ReportRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表配置的保存 / 加载 / 列表。
 * <p>
 * 配置以原样 JSON 存储（name/cellBindings/loopBlocks/summaries/params/template），
 * 打开报表时整体恢复前端状态。加载时附带数据模型信息（datasets + relationships）。
 * </p>
 */
@RestController
@RequestMapping("/api/report")
public class ReportConfigController {

    private final ReportRepository repository;
    private final DataModel dataModel;
    private final ReportTemplateSeeder templateSeeder;

    public ReportConfigController(ReportRepository repository, DataModel dataModel, ReportTemplateSeeder templateSeeder) {
        this.repository = repository;
        this.dataModel = dataModel;
        this.templateSeeder = templateSeeder;
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
        enriched.put("dataModel", buildDataModelDTO());
        return SingleResponse.of(enriched);
    }

    /** 示例报表列表（预存的测试报表，供快速打开）。 */
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

    // ============================================================
    // 数据模型 DTO 构建
    // ============================================================

    private Map<String, Object> buildDataModelDTO() {
        List<Map<String, Object>> datasets = dataModel.getDatasets().stream()
                .filter(ds -> ds instanceof TableDataset)
                .map(ds -> {
                    TableDataset tds = (TableDataset) ds;
                    List<Map<String, Object>> fields = tds.getFields().stream()
                            .map(f -> {
                                Map<String, Object> fm = new LinkedHashMap<>();
                                fm.put("name", f.getName());
                                fm.put("alias", f.getAlias());
                                fm.put("dataType", f.getDataType().name());
                                fm.put("primaryKey", f.isPrimaryKey());
                                return fm;
                            })
                            .toList();
                    Map<String, Object> dm = new LinkedHashMap<>();
                    dm.put("id", tds.getId());
                    dm.put("alias", tds.getAlias());
                    dm.put("fields", fields);
                    return dm;
                })
                .toList();

        List<Relationship> rels = dataModel.getRelationships();
        List<Map<String, Object>> relationships = (rels == null ? List.<Relationship>of() : rels).stream()
                .map(r -> {
                    Map<String, Object> rm = new LinkedHashMap<>();
                    rm.put("left", Map.of("datasetId", r.getLeft().datasetId(), "field", r.getLeft().field()));
                    rm.put("right", Map.of("datasetId", r.getRight().datasetId(), "field", r.getRight().field()));
                    rm.put("joinType", r.getJoinType() != null ? r.getJoinType().name() : "INNER");
                    return rm;
                })
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasets", datasets);
        result.put("relationships", relationships);
        return result;
    }
}
