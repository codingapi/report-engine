package com.codingapi.report.starter.controller;

import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.relation.Relationship;
import com.codingapi.report.starter.repository.ExampleReportRegistry;
import com.codingapi.report.starter.repository.ReportRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表配置的保存 / 加载 / 列表。
 * <p>
 * 配置以原样 JSON 存储（name/cellBindings/loopBlocks/summaries/params/template），
 * 打开报表时整体恢复前端状态。加载时附带数据模型信息（datasets + relationships）。
 * <p>
 * 存储交给 {@link ReportRepository}（使用方提供实现），示例报表 id 交给 {@link ExampleReportRegistry}。
 * </p>
 */
@RestController
@RequestMapping("/api/report")
@ConditionalOnClass(RestController.class)
public class ReportConfigController {

    private final ReportRepository repository;
    private final DataModel dataModel;
    private final ExampleReportRegistry exampleRegistry;

    public ReportConfigController(ReportRepository repository, DataModel dataModel, ExampleReportRegistry exampleRegistry) {
        this.repository = repository;
        this.dataModel = dataModel;
        this.exampleRegistry = exampleRegistry;
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
        List<ReportBrief> briefs = exampleRegistry.exampleReportIds().stream()
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
        // 构建 datasourceId → type 映射
        Map<String, String> sourceTypeMap = new LinkedHashMap<>();
        if (dataModel.getDatasources() != null) {
            for (DataSource ds : dataModel.getDatasources()) {
                sourceTypeMap.put(ds.getId(), ds.getType().name());
            }
        }

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
                    dm.put("dataSourceType", sourceTypeMap.getOrDefault(tds.getDatasourceId(), "CSV"));
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
