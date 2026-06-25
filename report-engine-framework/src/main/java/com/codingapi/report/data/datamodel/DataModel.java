package com.codingapi.report.data.datamodel;

import com.codingapi.report.config.dto.ConfigDtos.FieldRefDTO;
import com.codingapi.report.config.dto.DataModelDtos.DataModelDTO;
import com.codingapi.report.config.dto.DataModelDtos.DataSourceDTO;
import com.codingapi.report.config.dto.DataModelDtos.DatasetDTO;
import com.codingapi.report.config.dto.DataModelDtos.FieldDTO;
import com.codingapi.report.config.dto.DataModelDtos.RelationshipDTO;
import com.codingapi.report.config.dto.DataModelDtos.UnionMemberDTO;
import com.codingapi.report.data.dataset.DataType;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.dataset.UnionDataset;
import com.codingapi.report.data.dataset.UnionMember;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.type.DataSourceType;
import com.codingapi.report.data.relation.JoinType;
import com.codingapi.report.data.relation.RelationOrigin;
import com.codingapi.report.data.relation.Relationship;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * 数据模型（可复用的语义层）：描述"数据长什么样、表之间怎么关联"，<b>与具体报表无关</b>。
 *
 * <p>这是<b>领域对象</b>，也是 {@code DataModelRepository} 的持久化实体（不再有单独的 {@code DataModelConfig}）。 面向前端时通过
 * {@link #toDTO()} 转成 {@link DataModelDTO}（出口脱敏由上层做），前端 JSON 通过 {@link #fromDTO(DataModelDTO)} 还原。
 *
 * <h3>分层</h3>
 *
 * <p>建一次、多个 {@code Report} 引用（对齐 Power BI Tabular Model / Tableau Data Source / 帆软服务器数据集）。 数据计算全在 Java
 * 内存完成，连接（{@link DataSource}）只负责取规整表，故 {@link Relationship} 是内存 join 的 JoinSpec， 支持跨源关联（MySQL 表 JOIN
 * CSV 文件）。
 */
@Data
@Builder
public class DataModel {
    private String id;
    private String name;

    /** 状态（草稿/已发布）。 */
    private DataModelStatus status;

    /** 创建时间（epoch 毫秒）。 */
    private long createTime;

    /** 修改时间（epoch 毫秒）。 */
    private long updateTime;

    /**
     * 数据集列表：本模型选用的数据集，是报表绑定的最小粒度。
     *
     * <ul>
     *   <li>{@link TableDataset} —— 物理表，自带所属 {@link DataSource}（{@code dataset.getDatasource()}），
     *       故本模型不再单独持有 datasources 列表
     *   <li>{@link UnionDataset} —— UNION 派生（可跨源合并），定义在本模型内，无单一连接
     * </ul>
     */
    private List<Dataset> datasets;

    /** 跨数据集关联关系，所有引用本模型的报表共享（{@code RelationOrigin.AUTO} 自动扫描 / {@code MANUAL} 手动连线）。 */
    private List<Relationship> relationships;

    // ============================================================
    // 派生视图
    // ============================================================

    /** 去重收集本模型用到的连接（由各 TableDataset 自带的 datasource 聚合）。 */
    public List<DataSource> datasources() {
        Map<String, DataSource> byId = new LinkedHashMap<>();
        if (datasets != null) {
            for (Dataset ds : datasets) {
                if (ds instanceof TableDataset t && t.getDatasource() != null) {
                    byId.putIfAbsent(t.getDatasource().getId(), t.getDatasource());
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    // ============================================================
    // 领域 → DTO（出口；敏感字段脱敏由上层处理）
    // ============================================================

    public DataModelDTO toDTO() {
        return new DataModelDTO(
                id,
                name,
                status != null ? status.name() : null,
                createTime,
                updateTime,
                toDatasourceDtos(),
                toDatasetDtos(),
                toRelationshipDtos());
    }

    private List<DataSourceDTO> toDatasourceDtos() {
        List<DataSourceDTO> out = new ArrayList<>();
        for (DataSource s : datasources()) {
            out.add(
                    new DataSourceDTO(
                            s.getId(),
                            s.getName(),
                            s.getType() != null ? s.getType().type() : null,
                            s.getConfig()));
        }
        return out;
    }

    private List<DatasetDTO> toDatasetDtos() {
        List<DatasetDTO> out = new ArrayList<>();
        if (datasets == null) return out;
        for (Dataset ds : datasets) {
            if (ds instanceof TableDataset t) {
                out.add(
                        new DatasetDTO(
                                t.getId(),
                                t.getAlias(),
                                "TABLE",
                                t.getDatasourceId(),
                                t.getSourceTable(),
                                toFieldDtos(t.getFields()),
                                null));
            } else if (ds instanceof UnionDataset u) {
                out.add(
                        new DatasetDTO(
                                u.getId(),
                                u.getAlias(),
                                "UNION",
                                null,
                                null,
                                toFieldDtos(u.getFields()),
                                toUnionMemberDtos(u.getMembers())));
            }
        }
        return out;
    }

    private static List<FieldDTO> toFieldDtos(List<Field> fields) {
        List<FieldDTO> out = new ArrayList<>();
        if (fields == null) return out;
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

    private static List<UnionMemberDTO> toUnionMemberDtos(List<UnionMember> members) {
        List<UnionMemberDTO> out = new ArrayList<>();
        if (members == null) return out;
        for (UnionMember m : members) {
            out.add(new UnionMemberDTO(m.datasetId(), m.mapping()));
        }
        return out;
    }

    private List<RelationshipDTO> toRelationshipDtos() {
        List<RelationshipDTO> out = new ArrayList<>();
        if (relationships == null) return out;
        for (Relationship r : relationships) {
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

    private static FieldRefDTO toFieldRefDto(FieldRef ref) {
        return ref == null ? null : new FieldRefDTO(ref.datasetId(), ref.field());
    }

    // ============================================================
    // DTO → 领域（入口）
    // ============================================================

    public static DataModel fromDTO(DataModelDTO dto) {
        if (dto == null) return null;
        Map<String, DataSource> sources = buildSources(dto.datasources());
        List<Dataset> datasets = buildDatasets(dto.datasets(), sources);
        attachDatasetsToSources(datasets, sources);
        return DataModel.builder()
                .id(dto.id())
                .name(dto.name())
                .status(dto.status() != null ? DataModelStatus.valueOf(dto.status()) : null)
                .createTime(dto.createTime())
                .updateTime(dto.updateTime())
                .datasets(datasets)
                .relationships(buildRelationships(dto.relationships()))
                .build();
    }

    private static Map<String, DataSource> buildSources(List<DataSourceDTO> sources) {
        Map<String, DataSource> out = new LinkedHashMap<>();
        if (sources == null) return out;
        for (DataSourceDTO s : sources) {
            DataSourceType type =
                    s.type() != null ? DataSourceType.of(s.type(), s.config()) : null;
            out.put(
                    s.id(),
                    DataSource.builder()
                            .id(s.id())
                            .name(s.name())
                            .type(type)
                            .config(s.config())
                            .build());
        }
        return out;
    }

    private static List<Dataset> buildDatasets(
            List<DatasetDTO> datasets, Map<String, DataSource> sources) {
        List<Dataset> out = new ArrayList<>();
        if (datasets == null) return out;
        for (DatasetDTO d : datasets) {
            if ("UNION".equals(d.kind())) {
                out.add(
                        UnionDataset.builder()
                                .id(d.id())
                                .alias(d.alias())
                                .fields(buildFields(d.fields()))
                                .members(buildUnionMembers(d.members()))
                                .build());
            } else {
                out.add(
                        TableDataset.builder()
                                .id(d.id())
                                .alias(d.alias())
                                .datasource(sources.get(d.datasourceId()))
                                .datasourceId(d.datasourceId())
                                .sourceTable(d.sourceTable())
                                .fields(buildFields(d.fields()))
                                .build());
            }
        }
        return out;
    }

    private static void attachDatasetsToSources(
            List<Dataset> datasets, Map<String, DataSource> sources) {
        Map<String, List<Dataset>> grouped = new LinkedHashMap<>();
        for (Dataset ds : datasets) {
            if (ds instanceof TableDataset t && t.getDatasource() != null) {
                grouped.computeIfAbsent(t.getDatasource().getId(), k -> new ArrayList<>()).add(t);
            }
        }
        grouped.forEach(
                (id, list) -> {
                    DataSource s = sources.get(id);
                    if (s != null) s.setDatasets(list);
                });
    }

    private static List<Field> buildFields(List<FieldDTO> fields) {
        List<Field> out = new ArrayList<>();
        if (fields == null) return out;
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

    private static List<UnionMember> buildUnionMembers(List<UnionMemberDTO> members) {
        List<UnionMember> out = new ArrayList<>();
        if (members == null) return out;
        for (UnionMemberDTO m : members) {
            out.add(new UnionMember(m.datasetId(), m.mapping() != null ? m.mapping() : Map.of()));
        }
        return out;
    }

    public static List<Relationship> buildRelationships(List<RelationshipDTO> rels) {
        List<Relationship> out = new ArrayList<>();
        if (rels == null) return out;
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

    private static FieldRef toFieldRef(FieldRefDTO ref) {
        return ref == null ? null : new FieldRef(ref.datasetId(), ref.field());
    }
}
