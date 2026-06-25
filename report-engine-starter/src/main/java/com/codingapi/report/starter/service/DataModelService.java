package com.codingapi.report.starter.service;

import com.codingapi.report.config.DataModelConfig;
import com.codingapi.report.config.dto.DataModelDtos.DataSourceDTO;
import com.codingapi.report.config.dto.DataModelDtos.RelationshipDTO;
import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.datasource.converter.DataModelConfigConverter;
import com.codingapi.report.datasource.credential.CredentialService;
import com.codingapi.report.repository.DataModelRepository;
import com.codingapi.report.repository.PageQuery;
import com.codingapi.report.repository.PageResult;
import com.codingapi.report.starter.dto.DatasetDtos.DatasetDTO;
import com.codingapi.report.starter.dto.DatasetDtos.FieldDTO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据模型业务：CRUD + 凭证三态（merge/encrypt/mask）+ 运行时模型加载 + 数据集列表视图。
 *
 * <p>凭证安全在此层闭环：GET 出口脱敏、POST 入口回填 {@code ***} + 加密明文。 渲染/富化通过 {@link #loadDataModel} 复用同一加载与解密逻辑。
 */
public class DataModelService {

    private final DataModelRepository repository;
    private final CredentialService credentials;
    private final DataModelConfigConverter converter;

    public DataModelService(
            DataModelRepository repository,
            CredentialService credentials,
            DataModelConfigConverter converter) {
        this.repository = repository;
        this.credentials = credentials;
        this.converter = converter;
    }

    public PageResult<DataModelConfig> page(int current, int pageSize) {
        return repository.page(new PageQuery(current, pageSize));
    }

    /** 详情（{@code datasources.config} 脱敏）。 */
    public DataModelConfig getMasked(String id) {
        DataModelConfig cfg = repository.find(id);
        if (cfg == null) return null;
        cfg.setDatasources(maskAll(cfg.getDatasources()));
        return cfg;
    }

    /** 新建/更新：{@code ***} 凭证回填旧值，明文凭证加密后入库。 */
    public String save(DataModelConfig cfg) {
        DataModelConfig old =
                cfg.getId() != null && !cfg.getId().isBlank() ? repository.find(cfg.getId()) : null;
        mergeMaskedCredentials(cfg, old);
        encryptDatasourceConfigs(cfg);
        return repository.save(cfg);
    }

    public void delete(String id) {
        repository.delete(id);
    }

    /** 批量替换关系（列表式整体替换）。 */
    public void saveRelationships(String dataModelId, List<RelationshipDTO> relationships) {
        DataModelConfig cfg = repository.find(dataModelId);
        if (cfg == null) {
            throw new IllegalArgumentException("数据模型不存在: " + dataModelId);
        }
        cfg.setRelationships(relationships);
        repository.save(cfg);
    }

    /** 加载运行时 {@link DataModel}（解密）；不存在返回 null（富化容错用）。 */
    public DataModel findDataModel(String dataModelId) {
        if (dataModelId == null || dataModelId.isBlank()) return null;
        DataModelConfig cfg = repository.find(dataModelId);
        return cfg == null ? null : converter.toDataModel(cfg);
    }

    /** 加载运行时 {@link DataModel}（解密）；不存在或 id 缺失抛异常（渲染必须）。 */
    public DataModel loadDataModel(String dataModelId) {
        if (dataModelId == null || dataModelId.isBlank()) {
            throw new IllegalArgumentException("渲染请求缺少 dataModelId");
        }
        DataModel dm = findDataModel(dataModelId);
        if (dm == null) {
            throw new IllegalArgumentException("数据模型不存在: " + dataModelId);
        }
        return dm;
    }

    /** 列出数据集（含字段 + 来源类型，供左面板树）。 */
    public List<DatasetDTO> listDatasets(String dataModelId) {
        DataModel dm = loadDataModel(dataModelId);
        return dm.getDatasets().stream()
                .filter(ds -> ds instanceof TableDataset)
                .map(
                        ds -> {
                            TableDataset tds = (TableDataset) ds;
                            List<FieldDTO> fields =
                                    tds.getFields().stream()
                                            .map(
                                                    f ->
                                                            new FieldDTO(
                                                                    f.getName(),
                                                                    f.getAlias(),
                                                                    f.getDataType().name(),
                                                                    f.isPrimaryKey()))
                                            .toList();
                            return new DatasetDTO(
                                    tds.getId(),
                                    tds.getAlias(),
                                    tds.getDatasourceId(),
                                    sourceTypeOf(dm, tds.getDatasourceId()),
                                    fields);
                        })
                .toList();
    }

    private String sourceTypeOf(DataModel dm, String datasourceId) {
        return dm.getDatasources().stream()
                .filter(s -> s.getId().equals(datasourceId))
                .map(s -> s.getType().name())
                .findFirst()
                .orElse("CSV");
    }

    // ============================================================
    // 凭证 merge / 加密 / 脱敏
    // ============================================================

    private List<DataSourceDTO> maskAll(List<DataSourceDTO> sources) {
        if (sources == null) return null;
        return sources.stream()
                .map(
                        s ->
                                new DataSourceDTO(
                                        s.id(),
                                        s.name(),
                                        s.type(),
                                        credentials.maskConfig(s.config())))
                .toList();
    }

    /** 前端回传的 {@code ***} 占位用旧 config 的（已加密）值回填，避免覆盖真实凭证。 */
    private void mergeMaskedCredentials(DataModelConfig cfg, DataModelConfig old) {
        if (old == null || cfg.getDatasources() == null) return;
        Map<String, DataSourceDTO> oldById = new LinkedHashMap<>();
        for (DataSourceDTO s : old.getDatasources()) {
            oldById.put(s.id(), s);
        }
        for (DataSourceDTO neu : cfg.getDatasources()) {
            DataSourceDTO o = oldById.get(neu.id());
            if (o == null || neu.config() == null) continue;
            Map<String, Object> merged = new LinkedHashMap<>(neu.config());
            for (Map.Entry<String, Object> e : merged.entrySet()) {
                if (credentials.isMasked(e.getValue()) && o.config() != null) {
                    Object oldVal = o.config().get(e.getKey());
                    if (oldVal != null) {
                        merged.put(e.getKey(), oldVal);
                    }
                }
            }
            neu.config().clear();
            neu.config().putAll(merged);
        }
    }

    private void encryptDatasourceConfigs(DataModelConfig cfg) {
        if (cfg.getDatasources() == null) return;
        for (DataSourceDTO s : cfg.getDatasources()) {
            Map<String, Object> encrypted = credentials.encryptConfig(s.config());
            s.config().clear();
            s.config().putAll(encrypted);
        }
    }
}
