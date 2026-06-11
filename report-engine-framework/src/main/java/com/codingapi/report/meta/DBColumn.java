package com.codingapi.report.meta;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 字段
 */
@Setter
@Getter
public class DBColumn {

    /**
     * 字段key
     */
    private String key;
    /**
     * 名称
     */
    private String name;
    /**
     * 描述信息
     */
    private String description;
    /**
     * 字段类型
     */
    private DataType dataType;
    /**
     * 是否主键
     */
    private boolean primary;
    /**
     * 外建信息
     */
    private List<DBColumnKey> foreignKeys;

    public DBColumn() {
        this.foreignKeys = new ArrayList<>();
    }

    public void addForeignKey(String tableName,String columName){
        this.foreignKeys.add(new DBColumnKey(tableName,columName));
    }
}
