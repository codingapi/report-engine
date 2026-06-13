package com.example.report.model.source;

import lombok.Builder;
import lombok.Data;

/**
 * 关系层：跨数据集的关联（JOIN）。
 *
 * <p>挂在报表模型上，<b>不属于任何单个数据源</b>，因此可连接来自不同连接的数据集
 * （如人事库的"员工"与薪资库的"薪资"）。这正对应"单独数据源下关系不存在、
 * 多源汇到一起才产生联系"。
 */
@Data
@Builder
public class Relationship {
    private String id;
    private FieldRef left;
    private FieldRef right;
    private JoinType joinType;
    private RelationOrigin origin;
}
