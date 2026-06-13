package com.example.report.model.source;

import lombok.Builder;
import lombok.Data;

/** 数据集中的一个字段 */
@Data
@Builder
public class Field {
    /** 物理字段名 */
    private String name;
    /** 显示名/别名 */
    private String alias;
    private DataType dataType;
    private boolean primaryKey;
}
