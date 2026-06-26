package com.codingapi.report.dto.datamodel;

import java.util.List;

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
