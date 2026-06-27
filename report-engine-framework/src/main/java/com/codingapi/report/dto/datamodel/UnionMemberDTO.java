package com.codingapi.report.dto.datamodel;

import java.util.Map;

/** UNION 成员契约：{@code mapping} 为"统一列名 → 成员实际字段名"。 */
public record UnionMemberDTO(String datasetId, Map<String, String> mapping) {}
