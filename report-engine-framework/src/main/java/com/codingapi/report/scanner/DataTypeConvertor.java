package com.codingapi.report.scanner;

import com.codingapi.report.meta.DataType;

public class DataTypeConvertor {

    public static DataType toType(int jdbcType) {
        Class<?> javaType = JavaDBTypeConvertor.toJavaType(jdbcType);
        if (javaType.equals(String.class)) {
            return DataType.STRING;
        }

        if (javaType.equals(Long.class)) {
            return DataType.NUMBER;
        }

        if (javaType.equals(Integer.class)) {
            return DataType.NUMBER;
        }

        if (javaType.equals(Float.class)) {
            return DataType.NUMBER;
        }

        if (javaType.equals(Double.class)) {
            return DataType.NUMBER;
        }
        return null;
    }
}
