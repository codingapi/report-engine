package com.codingapi.report.data;

import com.codingapi.report.meta.DBTable;

import java.util.List;

/**
 * 数据资源信息
 */
public interface IDataSource {

    /**
     * 数据结构
     */
    List<DBTable> tables();

    /**
     * 数据内容
     */
    DataResult data(Object...params);


    /**
     * 参数定义
     */
    List<String> params();

}
