package com.codingapi.report.data.source;

import com.codingapi.report.context.JdbcTemplateContext;
import com.codingapi.report.data.DataResult;

/**
 * sql 查询数据对象
 */
public class SQLDataSource extends BaseDataSource {

    private final String sql;

    public SQLDataSource(String sql) {
        this.sql = sql;
    }

    @Override
    public DataResult data(Object... args) {
        return JdbcTemplateContext.getInstance().queryForList(this.sql, args);
    }

}
