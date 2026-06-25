package com.codingapi.report.config.dto;

import java.util.List;
import java.util.Map;

/**
 * 数据模型配置的 DTO 契约容器（前端 JSON ↔ 这些 record ↔ framework 领域对象）。
 *
 * <p>与 {@link ConfigDtos} 同范式：{@code Dataset} 是 sealed interface（{@code TableDataset}/ {@code
 * UnionDataset}），未加 Jackson 多态注解，无法直接反序列化，故用这些 record 承接持久化与前端 JSON， 再由 datasource 模块的 {@code
 * DataModelConfigConverter} 转为 framework 领域对象。
 *
 * <p>这些 record 同时是 {@code com.codingapi.report.config.DataModelConfig} 实体的字段类型（持久化契约）， 全字段强类型且
 * Jackson 可序列化。枚举值统一用 {@code String} 存 {@code name()}（{@link
 * com.codingapi.report.data.datasource.DataSourceType}/{@link
 * com.codingapi.report.data.dataset.DataType}/ {@link
 * com.codingapi.report.data.relation.JoinType}/{@link
 * com.codingapi.report.data.relation.RelationOrigin}）。
 *
 * <p>{@link DataSourceDTO#config()} 中敏感字段（password/secret/token/apiKey 等）存<b>加密值</b>， 由 datasource
 * 模块的 {@code CredentialService} 加解密、Controller 出口脱敏。
 */
public final class DataModelDtos {

    private DataModelDtos() {}

    /** 数据源（连接）持久化契约。{@code config} 含加密后的敏感字段。 */
    public record DataSourceDTO(String id, String name, String type, Map<String, Object> config) {}

    /** 字段定义持久化契约。 */
    public record FieldDTO(String name, String alias, String dataType, boolean primaryKey) {}

    /**
     * 数据集持久化契约。用 {@code kind} 区分两种形态（sealed {@code Dataset} 的扁平表达）：
     *
     * <ul>
     *   <li>{@code "TABLE"}：物理表，用 {@code datasourceId}/{@code sourceTable}/{@code fields}
     *   <li>{@code "UNION"}：UNION 派生，用 {@code fields}/{@code members}（无 datasourceId/sourceTable）
     * </ul>
     */
    public record DatasetDTO(
            String id,
            String alias,
            String kind,
            String datasourceId,
            String sourceTable,
            List<FieldDTO> fields,
            List<UnionMemberDTO> members) {}

    /** UNION 成员契约：{@code mapping} 为"统一列名 → 成员实际字段名"。 */
    public record UnionMemberDTO(String datasetId, Map<String, String> mapping) {}

    /** 跨数据集关系持久化契约。{@code left}/{@code right} 复用 {@link ConfigDtos.FieldRefDTO}。 */
    public record RelationshipDTO(
            String id,
            ConfigDtos.FieldRefDTO left,
            ConfigDtos.FieldRefDTO right,
            String joinType,
            String origin) {}
}
