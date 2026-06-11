package com.codingapi.report.meta;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * 字段标识
 */
@Setter
@Getter
@AllArgsConstructor
public class DBColumnKey {

    /**
     * 表名称
     */
    private String tableName;

    /**
     * 字段名称
     */
    private String columnName;

}
