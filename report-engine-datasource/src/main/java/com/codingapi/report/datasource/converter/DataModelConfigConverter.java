package com.codingapi.report.datasource.converter;

import com.codingapi.report.config.DataModelConfig;
import com.codingapi.report.config.dto.ConfigDtos.FieldRefDTO;
import com.codingapi.report.config.dto.DataModelDtos.DataSourceDTO;
import com.codingapi.report.config.dto.DataModelDtos.DatasetDTO;
import com.codingapi.report.config.dto.DataModelDtos.FieldDTO;
import com.codingapi.report.config.dto.DataModelDtos.RelationshipDTO;
import com.codingapi.report.config.dto.DataModelDtos.UnionMemberDTO;
import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.data.dataset.DataType;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.dataset.UnionDataset;
import com.codingapi.report.data.dataset.UnionMember;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.DataSourceType;
import com.codingapi.report.data.relation.JoinType;
import com.codingapi.report.data.relation.RelationOrigin;
import com.codingapi.report.data.relation.Relationship;
import com.codingapi.report.datasource.credential.CredentialService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link DataModel}（运行时领域对象）与 {@link DataModelConfig}（持久化实体）互转。
 *
 * <p>转换处衔接凭证三态：
 *
 * <ul>
 *   <li>{@link #toConfig(DataModel)}：domain → DTO，{@code DataSource.config} 走 {@link
 *       CredentialService#encryptConfig}
 *   <li>{@link #toDataModel(DataModelConfig)}：DTO → domain，{@code config} 走 {@link
 *       CredentialService#decryptConfig}
 * </ul>
 *
 * <p>{@link Dataset} 是 sealed（{@code TableDataset}/{@code UnionDataset}），DTO 用 {@code kind} 字段扁平区分。
 */
public class DataModelConfigConverter {

    private final CredentialService credentials;

    public DataModelConfigConverter(CredentialService credentials) {
        this.credentials = credentials;
    }

    // ============================================================
    // domain → 持久化实体
    // ============================================================

    public DataModelConfig toConfig(DataModel dm) {
        DataModelConfig cfg = new DataModelConfig();
        cfg.setId(dm.getId());
        cfg.setName(dm.getName());
        cfg.setStatus("PUBLISHED");
        cfg.setDatasources(toDataSourceDtoList(dm.getDatasources()));
        cfg.setDatasets(toDatasetDtoList(dm.getDatasets()));
        cfg.setRelationships(toRelationshipDtoList(dm.getRelationships()));
        return cfg;
    }

    private List<DataSourceDTO> toDataSourceDtoList(List<DataSource> sources) {
        if (sources == null) return List.of();
        List<DataSourceDTO> out = new ArrayList<>(sources.size());
        for (DataSource s : sources) {
            out.add(
                    new DataSourceDTO(
                            s.getId(),
                            s.getName(),
                            s.getType() != null ? s.getType().name() : null,
                            credentials.encryptConfig(s.getConfig())));
        }
        return out;
    }

    private List<DatasetDTO> toDatasetDtoList(List<Dataset> datasets) {
        if (datasets == null) return List.of();
        List<DatasetDTO> out = new ArrayList<>(datasets.size());
        for (Dataset ds : datasets) {
            if (ds instanceof TableDataset t) {
                out.add(
                        new DatasetDTO(
                                t.getId(),
                                t.getAlias(),
                                "TABLE",
                                t.getDatasourceId(),
                                t.getSourceTable(),
                                toFieldDtoList(t.getFields()),
                                null));
            } else if (ds instanceof UnionDataset u) {
                out.add(
                        new DatasetDTO(
                                u.getId(),
                                u.getAlias(),
                                "UNION",
                                null,
                                null,
                                toFieldDtoList(u.getFields()),
                                toUnionMemberDtoList(u.getMembers())));
            }
        }
        return out;
    }

    private List<FieldDTO> toFieldDtoList(List<Field> fields) {
        if (fields == null) return List.of();
        List<FieldDTO> out = new ArrayList<>(fields.size());
        for (Field f : fields) {
            out.add(
                    new FieldDTO(
                            f.getName(),
                            f.getAlias(),
                            f.getDataType() != null ? f.getDataType().name() : null,
                            f.isPrimaryKey()));
        }
        return out;
    }

    private List<UnionMemberDTO> toUnionMemberDtoList(List<UnionMember> members) {
        if (members == null) return List.of();
        List<UnionMemberDTO> out = new ArrayList<>(members.size());
        for (UnionMember m : members) {
            out.add(new UnionMemberDTO(m.datasetId(), m.mapping()));
        }
        return out;
    }

    private List<RelationshipDTO> toRelationshipDtoList(List<Relationship> rels) {
        if (rels == null) return List.of();
        List<RelationshipDTO> out = new ArrayList<>(rels.size());
        for (Relationship r : rels) {
            out.add(
                    new RelationshipDTO(
                            r.getId(),
                            toFieldRefDto(r.getLeft()),
                            toFieldRefDto(r.getRight()),
                            r.getJoinType() != null ? r.getJoinType().name() : null,
                            r.getOrigin() != null ? r.getOrigin().name() : null));
        }
        return out;
    }

    private FieldRefDTO toFieldRefDto(FieldRef ref) {
        return ref == null ? null : new FieldRefDTO(ref.datasetId(), ref.field());
    }

    // ============================================================
    // 持久化实体 → domain
    // ============================================================

    public DataModel toDataModel(DataModelConfig cfg) {
        if (cfg == null) return null;
        return DataModel.builder()
                .id(cfg.getId())
                .name(cfg.getName())
                .datasources(toDataSourceList(cfg.getDatasources()))
                .datasets(toDatasetList(cfg.getDatasets()))
                .relationships(toRelationshipList(cfg.getRelationships()))
                .build();
    }

    private List<DataSource> toDataSourceList(List<DataSourceDTO> sources) {
        if (sources == null) return List.of();
        List<DataSource> out = new ArrayList<>(sources.size());
        for (DataSourceDTO s : sources) {
            out.add(
                    DataSource.builder()
                            .id(s.id())
                            .name(s.name())
                            .type(s.type() != null ? DataSourceType.valueOf(s.type()) : null)
                            .config(credentials.decryptConfig(s.config()))
                            .build());
        }
        return out;
    }

    private List<Dataset> toDatasetList(List<DatasetDTO> datasets) {
        if (datasets == null) return List.of();
        List<Dataset> out = new ArrayList<>(datasets.size());
        for (DatasetDTO d : datasets) {
            if ("UNION".equals(d.kind())) {
                out.add(
                        UnionDataset.builder()
                                .id(d.id())
                                .alias(d.alias())
                                .fields(toFieldList(d.fields()))
                                .members(toUnionMemberList(d.members()))
                                .build());
            } else {
                out.add(
                        TableDataset.builder()
                                .id(d.id())
                                .alias(d.alias())
                                .datasourceId(d.datasourceId())
                                .sourceTable(d.sourceTable())
                                .fields(toFieldList(d.fields()))
                                .build());
            }
        }
        return out;
    }

    private List<Field> toFieldList(List<FieldDTO> fields) {
        if (fields == null) return List.of();
        List<Field> out = new ArrayList<>(fields.size());
        for (FieldDTO f : fields) {
            out.add(
                    Field.builder()
                            .name(f.name())
                            .alias(f.alias())
                            .dataType(
                                    f.dataType() != null
                                            ? DataType.valueOf(f.dataType())
                                            : DataType.STRING)
                            .primaryKey(f.primaryKey())
                            .build());
        }
        return out;
    }

    private List<UnionMember> toUnionMemberList(List<UnionMemberDTO> members) {
        if (members == null) return List.of();
        List<UnionMember> out = new ArrayList<>(members.size());
        for (UnionMemberDTO m : members) {
            out.add(new UnionMember(m.datasetId(), m.mapping() != null ? m.mapping() : Map.of()));
        }
        return out;
    }

    private List<Relationship> toRelationshipList(List<RelationshipDTO> rels) {
        if (rels == null) return List.of();
        List<Relationship> out = new ArrayList<>(rels.size());
        for (RelationshipDTO r : rels) {
            out.add(
                    Relationship.builder()
                            .id(r.id())
                            .left(toFieldRef(r.left()))
                            .right(toFieldRef(r.right()))
                            .joinType(
                                    r.joinType() != null
                                            ? JoinType.valueOf(r.joinType())
                                            : JoinType.INNER)
                            .origin(
                                    r.origin() != null
                                            ? RelationOrigin.valueOf(r.origin())
                                            : RelationOrigin.MANUAL)
                            .build());
        }
        return out;
    }

    private FieldRef toFieldRef(FieldRefDTO ref) {
        return ref == null ? null : new FieldRef(ref.datasetId(), ref.field());
    }
}
