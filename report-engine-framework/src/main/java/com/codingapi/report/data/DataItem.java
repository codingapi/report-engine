package com.codingapi.report.data;

import com.codingapi.report.meta.DataType;
import lombok.Getter;
import lombok.Setter;

/**
 * 数据项
 */
@Setter
@Getter
public class DataItem {

    private String name;
    private Object value;
    private DataType type;

    public DataItem(String name, Object value) {
        this.name = name;
        this.value = value;
        this.toType(value);
    }

    private void toType(Object value) {
        if (value != null) {
            if (value instanceof String) {
                this.type = DataType.STRING;
            }
            if (value instanceof Boolean) {
                this.type = DataType.BOOLEAN;
            }
            if (value instanceof Long) {
                this.type = DataType.NUMBER;
            }
            if (value instanceof Integer) {
                this.type = DataType.NUMBER;
            }
            if (value instanceof Double) {
                this.type = DataType.NUMBER;
            }
            if (value instanceof Float) {
                this.type = DataType.NUMBER;
            }
        }
    }
}
