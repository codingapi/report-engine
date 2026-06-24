package com.example.report.config;

import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.data.dataset.DataType;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.DataSourceType;
import com.codingapi.report.data.datasource.csv.CsvDataExtractor;
import com.codingapi.report.data.relation.JoinType;
import com.codingapi.report.data.relation.RelationOrigin;
import com.codingapi.report.data.relation.Relationship;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * CSV 数据集自动配置：扫描 classpath 下的 JSON 描述文件，构建 DataModel。
 *
 * <p>每个数据集由一对文件定义：
 *
 * <ul>
 *   <li>{@code data/{name}.csv} — 数据文件
 *   <li>{@code data/{name}.json} — 字段描述（alias、字段列表、类型）
 * </ul>
 */
@Slf4j
@Configuration
public class DatasetConfig {

    @Value("${report.datasets.dir:data/}")
    private String datasetsDir;

    @Bean
    public CsvDataExtractor csvDataExtractor() {
        return new CsvDataExtractor();
    }

    private List<Relationship> loadRelationships(ObjectMapper mapper) {
        String path = "classpath:" + datasetsDir + "relationships.json";
        try {
            PathMatchingResourcePatternResolver resolver =
                    new PathMatchingResourcePatternResolver();
            Resource resource = resolver.getResource(path);
            if (!resource.exists()) return List.of();

            try (InputStream is = resource.getInputStream()) {
                JsonNode arr = mapper.readTree(is);
                List<Relationship> result = new ArrayList<>();
                int idx = 0;
                for (JsonNode node : arr) {
                    JsonNode l = node.get("left");
                    JsonNode r = node.get("right");
                    result.add(
                            Relationship.builder()
                                    .id("rel-" + (++idx))
                                    .left(
                                            new FieldRef(
                                                    l.get("datasetId").asText(),
                                                    l.get("field").asText()))
                                    .right(
                                            new FieldRef(
                                                    r.get("datasetId").asText(),
                                                    r.get("field").asText()))
                                    .joinType(JoinType.valueOf(node.get("joinType").asText()))
                                    .origin(RelationOrigin.MANUAL)
                                    .build());
                }
                log.info("加载关系: {} 条", result.size());
                return result;
            }
        } catch (Exception e) {
            log.warn("加载关系文件失败: {}", e.getMessage());
            return List.of();
        }
    }

    @Bean
    public DataModel dataModel() throws Exception {
        String pattern = "classpath:" + datasetsDir + "*.json";
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(pattern);

        ObjectMapper mapper = new ObjectMapper();
        List<DataSource> datasources = new ArrayList<>();
        List<Dataset> datasets = new ArrayList<>();

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null || "relationships.json".equals(filename)) continue;
            String id = filename.replace(".json", "");

            try (InputStream is = resource.getInputStream()) {
                JsonNode root = mapper.readTree(is);

                String alias = root.has("alias") ? root.get("alias").asText() : id;

                List<Field> fields = new ArrayList<>();
                for (JsonNode fn : root.get("fields")) {
                    fields.add(
                            Field.builder()
                                    .name(fn.get("name").asText())
                                    .alias(fn.has("alias") ? fn.get("alias").asText() : null)
                                    .dataType(DataType.valueOf(fn.get("dataType").asText()))
                                    .primaryKey(
                                            fn.has("primaryKey")
                                                    && fn.get("primaryKey").asBoolean())
                                    .build());
                }

                // 每个数据集一个 DataSource（CsvDataExtractor 从 config.path 读 CSV）
                String csvPath = "/" + datasetsDir + id + ".csv";
                DataSource source =
                        DataSource.builder()
                                .id("csv_" + id)
                                .name(alias)
                                .type(DataSourceType.CSV)
                                .config(Map.of("path", csvPath))
                                .build();
                datasources.add(source);

                TableDataset ds =
                        TableDataset.builder()
                                .id(id)
                                .datasourceId(source.getId())
                                .sourceTable(id + ".csv")
                                .alias(alias)
                                .fields(fields)
                                .build();
                datasets.add(ds);
                log.info("加载数据集: {} ({}) - {} 个字段, path={}", id, alias, fields.size(), csvPath);
            }
        }

        // 加载关系
        List<Relationship> relationships = loadRelationships(mapper);

        return DataModel.builder()
                .id("default")
                .name("默认数据模型")
                .datasources(datasources)
                .datasets(new ArrayList<>(datasets))
                .relationships(relationships)
                .build();
    }
}
