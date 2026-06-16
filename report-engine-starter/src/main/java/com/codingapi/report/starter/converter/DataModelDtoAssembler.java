package com.codingapi.report.starter.converter;

import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.relation.Relationship;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 framework 的 {@link DataModel} 组装为前端可消费的 DTO（datasets + relationships）。
 * <p>
 * 供 {@code GET /api/report/configs/{id}} 在加载配置时附带返回数据模型信息。无状态（static）。
 */
public final class DataModelDtoAssembler {

    private DataModelDtoAssembler() {
    }

    public static Map<String, Object> assemble(DataModel dataModel) {
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
