package com.example.report.model.source;

import java.util.Map;

/**
 * UNION 成员：把一个数据集的字段映射到 union 数据集的统一列。
 *
 * @param datasetId 成员数据集 id
 * @param mapping   统一列名 → 成员字段名（成员各自字段名可不同，在此对齐）
 */
public record UnionMember(String datasetId, Map<String, String> mapping) {
}
