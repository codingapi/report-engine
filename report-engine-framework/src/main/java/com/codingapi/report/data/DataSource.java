package com.codingapi.report.data;

import lombok.Getter;
import lombok.Setter;

/**
 * 数据对象
 */
@Setter
@Getter
public class DataSource {

    /**
     * 对象名称
     */
    private String name;

    /**
     * 数据源
     */
    private IDataSource source;


}
