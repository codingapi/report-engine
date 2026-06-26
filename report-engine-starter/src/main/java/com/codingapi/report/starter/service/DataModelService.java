package com.codingapi.report.starter.service;

import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.credential.CredentialService;
import com.codingapi.report.dto.datamodel.DataModelDTO;
import com.codingapi.report.dto.datamodel.DataSourceDTO;
import com.codingapi.report.dto.datamodel.RelationshipDTO;
import com.codingapi.report.repository.DataModelRepository;
import com.codingapi.report.repository.DataSourceRepository;
import com.codingapi.report.repository.PageQuery;
import com.codingapi.report.repository.PageResult;
import com.codingapi.report.starter.dto.DatasetDtos.DatasetDTO;
import com.codingapi.report.starter.dto.DatasetDtos.FieldDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据模型业务：CRUD + 凭证（出口脱敏 / *** 回填）+ 数据集列表视图。
 *
 * <p>仓库以领域 {@link DataModel} 存取（{@code config} 明文，落盘加密交仓库实现）。出入站用 {@link DataModelDTO}： {@link
 * DataModel#toDTO()} / {@link DataModel#fromDTO} 互转，敏感字段仅在出口 {@link #getMasked} 脱敏。
 *
 * <p>连接解析：{@link DataModel} 持久化时仅存 {@code datasourceId} 引用，加载时由 {@link DataSourceRepository}
 * 解析为真实 {@link DataSource} 注入到 {@link TableDataset} 中，使全局连接（{@code /api/datasources} 管理）成为唯一权威来源。
 */
@Slf4j
public class DataModelService {

    private final DataModelRepository repository;
    private final DataSourceRepository dataSourceRepository;
    private final CredentialService credentials;

    public DataModelService(
            DataModelRepository repository,
            DataSourceRepository dataSourceRepository,
            CredentialService credentials) {
        this.repository = repository;
        this.dataSourceRepository = dataSourceRepository;
        this.credentials = credentials;
    }

    public PageResult<DataModel> page(int current, int pageSize) {
        return repository.page(new PageQuery(current, pageSize));
    }

    /** 详情（{@code datasources.config} 脱敏）。 */
    public DataModelDTO getMasked(String id) {
        DataModel dm = repository.find(id);
        if (dm == null) return null;
        resolveDatasources(dm);
        return maskDto(dm.toDTO());
    }

    /**
     * 加载时按 {@code datasourceId} 解析全局 {@link DataSource} 注入到 {@link TableDataset}。 已有嵌入连接（旧数据/演示）保持不变，仅补齐缺失。
     */
    private void resolveDatasources(DataModel dm) {
        if (dm == null || dm.getDatasets() == null) return;
        for (Dataset ds : dm.getDatasets()) {
            if (!(ds instanceof TableDataset t)) continue;
            if (t.getDatasource() != null) continue;
            String refId = t.getDatasourceId();
            if (refId == null || refId.isBlank() || dataSourceRepository == null) {
                log.warn("数据集 {} 缺少 datasourceId，无法解析连接", t.getId());
                continue;
            }
            DataSource src = dataSourceRepository.find(refId);
            if (src == null) {
                log.warn("数据集 {} 引用的连接 {} 不存在（DataSourceRepository.find 返回 null）", t.getId(), refId);
                continue;
            }
            t.setDatasource(src);
        }
    }

    /** 新建/更新：{@code ***} 凭证回填旧值（明文存储，落盘加密交仓库实现）。 */
    public String save(DataModelDTO dto) {
        DataModel incoming = DataModel.fromDTO(dto);
        DataModel old = dto.id() != null && !dto.id().isBlank() ? repository.find(dto.id()) : null;
        mergeMaskedCredentials(incoming, old);
        return repository.save(incoming);
    }

    public void delete(String id) {
        repository.delete(id);
    }

    /** 批量替换关系（列表式整体替换）。 */
    public void saveRelationships(String dataModelId, List<RelationshipDTO> relationships) {
        DataModel dm = repository.find(dataModelId);
        if (dm == null) {
            throw new IllegalArgumentException("数据模型不存在: " + dataModelId);
        }
        dm.setRelationships(DataModel.buildRelationships(relationships));
        repository.save(dm);
    }

    /** 加载领域模型；不存在返回 null（富化容错用）。 */
    public DataModel findDataModel(String dataModelId) {
        if (dataModelId == null || dataModelId.isBlank()) return null;
        DataModel dm = repository.find(dataModelId);
        resolveDatasources(dm);
        return dm;
    }

    /** 加载领域模型；不存在或 id 缺失抛异常（渲染必须）。 */
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
        List<DatasetDTO> out = new ArrayList<>();
        if (dm.getDatasets() == null) return out;
        for (Dataset ds : dm.getDatasets()) {
            if (!(ds instanceof TableDataset tds)) continue;
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
            String type =
                    tds.getDatasource() != null ? tds.getDatasource().getType().type() : "CSV";
            out.add(
                    new DatasetDTO(
                            tds.getId(), tds.getAlias(), tds.getDatasourceId(), type, fields));
        }
        return out;
    }

    // ============================================================
    // 凭证 mask / merge
    // ============================================================

    private DataModelDTO maskDto(DataModelDTO dto) {
        if (dto.datasources() == null) return dto;
        List<DataSourceDTO> masked =
                dto.datasources().stream()
                        .map(
                                s ->
                                        new DataSourceDTO(
                                                s.id(),
                                                s.name(),
                                                s.type(),
                                                s.typeConfigId(),
                                                credentials.maskConfig(s.config())))
                        .toList();
        return new DataModelDTO(
                dto.id(),
                dto.name(),
                dto.status(),
                dto.createTime(),
                dto.updateTime(),
                masked,
                dto.datasets(),
                dto.relationships());
    }

    /** 前端回传的 {@code ***} 占位用旧连接的真实值回填（按连接 id 匹配），避免覆盖真实凭证。 */
    private void mergeMaskedCredentials(DataModel incoming, DataModel old) {
        if (old == null) return;
        Map<String, DataSource> oldById = new LinkedHashMap<>();
        for (DataSource s : old.datasources()) {
            oldById.put(s.getId(), s);
        }
        for (DataSource neu : incoming.datasources()) {
            DataSource o = oldById.get(neu.getId());
            if (o == null || neu.getConfig() == null || o.getConfig() == null) continue;
            Map<String, Object> merged = new LinkedHashMap<>(neu.getConfig());
            boolean changed = false;
            for (Map.Entry<String, Object> e : merged.entrySet()) {
                if (credentials.isMasked(e.getValue())) {
                    Object oldVal = o.getConfig().get(e.getKey());
                    if (oldVal != null) {
                        e.setValue(oldVal);
                        changed = true;
                    }
                }
            }
            if (changed) neu.setConfig(merged);
        }
    }
}
