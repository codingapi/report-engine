package com.codingapi.report.starter.service;

import com.codingapi.report.config.dto.DataModelDtos.DataSourceDTO;
import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.datasource.ColumnMeta;
import com.codingapi.report.data.datasource.DataExtractor;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.DataSourceType;
import com.codingapi.report.data.datasource.RawTable;
import com.codingapi.report.data.datasource.TestResult;
import com.codingapi.report.starter.dto.DatasetDtos.PreviewDTO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据源（连接）操作业务：连接测试 + 表/列探查 + 数据集预览。
 *
 * <p>提取器按 {@code supports(type)} 在 {@code List<DataExtractor>} 中派发，与渲染链路同一注册表范式。 预览/探查 的连接从 {@link
 * DataModelService#loadDataModel} 取（已解密）。
 */
public class DataSourceService {

    private final DataModelService dataModelService;
    private final List<DataExtractor> extractors;

    public DataSourceService(DataModelService dataModelService, List<DataExtractor> extractors) {
        this.dataModelService = dataModelService;
        this.extractors = extractors;
    }

    /** 测试连接（不落库，凭证明文）。 */
    public TestResult testConnection(DataSourceDTO dto) {
        DataSourceType type = DataSourceType.valueOf(dto.type());
        DataSource ds =
                DataSource.builder()
                        .id(dto.id())
                        .name(dto.name())
                        .type(type)
                        .config(dto.config())
                        .build();
        return findExtractor(type).test(ds);
    }

    public List<String> listTables(String dataModelId, String datasourceId) {
        DataSource ds = loadDataSource(dataModelId, datasourceId);
        return findExtractor(ds.getType()).listTables(ds);
    }

    public List<ColumnMeta> listColumns(String dataModelId, String datasourceId, String table) {
        DataSource ds = loadDataSource(dataModelId, datasourceId);
        return findExtractor(ds.getType()).listColumns(ds, table);
    }

    /** 数据集预览：提取前 N 行，去掉列名 datasetId 前缀。 */
    public PreviewDTO previewDataset(String dataModelId, String datasetId, int limit) {
        DataModel dm = dataModelService.loadDataModel(dataModelId);
        Dataset ds =
                dm.getDatasets().stream()
                        .filter(d -> d.getId().equals(datasetId))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("数据集不存在: " + datasetId));
        if (!(ds instanceof TableDataset tds)) {
            throw new IllegalArgumentException("非表格数据集: " + datasetId);
        }
        DataSource source =
                dm.getDatasources().stream()
                        .filter(s -> s.getId().equals(tds.getDatasourceId()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "数据源不存在: " + tds.getDatasourceId()));
        RawTable raw = findExtractor(source.getType()).extract(source, tds);

        String prefix = tds.getId() + ".";
        List<String> columns =
                raw.getColumns().stream()
                        .map(c -> c.startsWith(prefix) ? c.substring(prefix.length()) : c)
                        .toList();
        List<Map<String, Object>> rows =
                raw.getRows().stream()
                        .limit(limit)
                        .map(
                                row -> {
                                    Map<String, Object> simplified = new LinkedHashMap<>();
                                    for (Map.Entry<String, Object> e : row.entrySet()) {
                                        String key = e.getKey();
                                        simplified.put(
                                                key.startsWith(prefix)
                                                        ? key.substring(prefix.length())
                                                        : key,
                                                e.getValue());
                                    }
                                    return simplified;
                                })
                        .toList();
        return new PreviewDTO(columns, rows);
    }

    private DataExtractor findExtractor(DataSourceType type) {
        return extractors.stream()
                .filter(e -> e.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("无提取器支持类型: " + type));
    }

    private DataSource loadDataSource(String dataModelId, String datasourceId) {
        DataModel dm = dataModelService.loadDataModel(dataModelId);
        return dm.getDatasources().stream()
                .filter(s -> s.getId().equals(datasourceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("连接不存在: " + datasourceId));
    }
}
