package com.example.report.controller;

import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.DataSourceType;
import com.codingapi.report.data.datasource.RawTable;
import com.codingapi.report.data.datasource.csv.CsvDataExtractor;
import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.springboot.framework.dto.response.MultiResponse;
import com.codingapi.springboot.framework.dto.response.SingleResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private final DataModel dataModel;
    private final CsvDataExtractor csvExtractor;

    public DatasetController(DataModel dataModel, CsvDataExtractor csvExtractor) {
        this.dataModel = dataModel;
        this.csvExtractor = csvExtractor;
    }

    /** 列出所有数据集（含字段定义） */
    @GetMapping
    public MultiResponse<DatasetDTO> list() {
        List<DatasetDTO> list = dataModel.getDatasets().stream()
                .filter(ds -> ds instanceof TableDataset)
                .map(ds -> {
                    TableDataset tds = (TableDataset) ds;
                    List<FieldDTO> fields = tds.getFields().stream()
                            .map(f -> new FieldDTO(
                                    f.getName(),
                                    f.getAlias(),
                                    f.getDataType().name(),
                                    f.isPrimaryKey()))
                            .toList();
                    return new DatasetDTO(
                            tds.getId(),
                            tds.getAlias(),
                            tds.getDatasourceId(),
                            "CSV",
                            fields);
                })
                .toList();
        return MultiResponse.of(list);
    }

    /** 预览数据集前 N 行 */
    @GetMapping("/{id}/preview")
    public SingleResponse<PreviewDTO> preview(
            @PathVariable String id,
            @RequestParam(defaultValue = "20") int limit) {

        Dataset ds = dataModel.getDatasets().stream()
                .filter(d -> d.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("数据集不存在: " + id));

        if (!(ds instanceof TableDataset tds)) {
            throw new IllegalArgumentException("非表格数据集: " + id);
        }

        DataSource source = DataSource.builder()
                .id("csv")
                .name("CSV")
                .type(DataSourceType.CSV)
                .config(Map.of("path", "/data/" + tds.getSourceTable()))
                .build();

        RawTable raw = csvExtractor.extract(source, tds);

        // 取前 limit 行，去掉列名的 datasetId 前缀
        String prefix = tds.getId() + ".";
        List<String> columns = raw.getColumns().stream()
                .map(c -> c.startsWith(prefix) ? c.substring(prefix.length()) : c)
                .toList();

        List<Map<String, Object>> rows = raw.getRows().stream()
                .limit(limit)
                .map(row -> {
                    Map<String, Object> simplified = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> e : row.entrySet()) {
                        String key = e.getKey();
                        simplified.put(key.startsWith(prefix) ? key.substring(prefix.length()) : key, e.getValue());
                    }
                    return simplified;
                })
                .toList();

        return SingleResponse.of(new PreviewDTO(columns, rows));
    }

    // ============================================================
    // DTO
    // ============================================================

    public record DatasetDTO(
            String id,
            String alias,
            String dataSourceId,
            String dataSourceType,
            List<FieldDTO> fields) {
    }

    public record FieldDTO(
            String name,
            String alias,
            String dataType,
            boolean primaryKey) {
    }

    public record PreviewDTO(
            List<String> columns,
            List<Map<String, Object>> rows) {
    }
}
