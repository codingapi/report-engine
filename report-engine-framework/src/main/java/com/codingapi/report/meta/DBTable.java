package com.codingapi.report.meta;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据表
 */
@Setter
@Getter
public class DBTable {

    /**
     * 名称
     */
    private String name;
    /**
     * 描述
     */
    private String description;
    /**
     * 字段
     */
    private List<DBColumn> columns;


    public DBTable(String name, String description) {
        this.name = name;
        this.description = description;
        this.columns = new ArrayList<>();
    }


    /**
     * 添加字段
     */
    public void addColumn(DBColumn column){
        this.columns.add(column);
    }

    /**
     * 获取字段
     *
     * @param columName 字段Key
     * @return 字段类型
     */
    public DBColumn getColumnByName(String columName) {
        if (this.columns != null) {
            for (DBColumn column : this.columns) {
                if (column.getName().equalsIgnoreCase(columName)) {
                    return column;
                }
            }
        }
        return null;
    }


    public void addPrimaryKey(String columnName) {
        DBColumn dbColumn = this.getColumnByName(columnName);
        if(dbColumn!=null){
            dbColumn.setPrimary(true);
        }
    }
}
