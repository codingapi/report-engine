package com.codingapi.report.format;

/**
 * 字符串类型
 */
public class StringFormat implements DataFormat{

    @Override
    public String format(Object value) {
        return value.toString();
    }
}
