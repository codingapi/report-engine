package com.example.report.config;

import com.codingapi.report.config.DataModelConfig;
import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.data.dataset.DataType;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.DataSourceType;
import com.codingapi.report.data.relation.JoinType;
import com.codingapi.report.data.relation.RelationOrigin;
import com.codingapi.report.data.relation.Relationship;
import com.codingapi.report.datasource.converter.DataModelConfigConverter;
import com.codingapi.report.repository.DataModelRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * 默认数据模型 seeder：启动时扫描 classpath 下的 CSV 数据集描述，构建 {@code id="default"} 的数据模型配置写入 {@link
 * DataModelRepository}。范式同 {@link ReportTemplateSeeder}（稳定 id，重启幂等）。
 *
 * <p>取代原 {@code DatasetConfig} 的 {@code @Bean DataModel}：数据模型不再以单例 Bean 提供， 改走仓库按 id 加载（多模型）。CSV
 * 提取器 Bean 由 datasource 模块自动配置注册，不再在此声明。
 *
 * <p>每个数据集由一对文件定义：
 *
 * <ul>
 *   <li>{@code data/{name}.csv} — 数据文件
 *   <li>{@code data/{name}.json} — 字段描述（alias、字段列表、类型）
 * </ul>
 */
@Slf4j
@Component
public class DataModelSeeder {

    @Value("${report.datasets.dir:data/}")
    private String datasetsDir;

    private final DataModelRepository repository;
    private final DataModelConfigConverter converter;

    public DataModelSeeder(DataModelRepository repository, DataModelConfigConverter converter) {
        this.repository = repository;
        this.converter = converter;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (repository.find("default") != null) {
            log.info("默认数据模型已存在，跳过 seeder");
            return;
        }
        try {
            DataModel dm = buildDefaultModel();
            DataModelConfig cfg = converter.toConfig(dm);
            cfg.setId("default");
            repository.save(cfg);
            log.info(
                    "已预存默认数据模型: {} 个数据集, {} 条关系",
                    dm.getDatasets().size(),
                    dm.getRelationships() != null ? dm.getRelationships().size() : 0);
        } catch (Exception e) {
            log.error("默认数据模型 seeder 失败", e);
        }
    }

    private DataModel buildDefaultModel() throws Exception {
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

        List<Relationship> relationships = loadRelationships(mapper);

        return DataModel.builder()
                .id("default")
                .name("默认数据模型")
                .datasources(datasources)
                .datasets(new ArrayList<>(datasets))
                .relationships(relationships)
                .build();
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
}
