package com.codingapi.report.dto.datamodel;

import com.codingapi.report.dto.report.FieldRefDTO;

/** 跨数据集关系持久化契约。{@code left}/{@code right} 复用 {@link FieldRefDTO}。 */
public record RelationshipDTO(
        String id,
        FieldRefDTO left,
        FieldRefDTO right,
        String joinType,
        String origin) {}
