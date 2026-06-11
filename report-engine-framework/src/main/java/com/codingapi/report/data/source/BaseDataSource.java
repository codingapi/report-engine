package com.codingapi.report.data.source;

import com.codingapi.report.data.DataResult;
import com.codingapi.report.data.DataRow;
import com.codingapi.report.data.IDataSource;
import com.codingapi.report.exception.DataSourceParamVerifyException;
import com.codingapi.report.meta.DBTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class BaseDataSource implements IDataSource {

    private List<String> params;
    private List<DBTable> tables;

    public BaseDataSource() {
        this.params = new ArrayList<>();
        this.tables = new ArrayList<>();
    }

    public void addTable(DBTable table) {
        this.tables.add(table);
    }

    public void addParam(String param) {
        this.params.add(param);
    }

    @Override
    public List<DBTable> tables() {
        return this.tables;
    }

    @Override
    public List<String> params() {
        return this.params;
    }


    private void verifyParams(Map<String, Object> params) {
        if (this.params != null) {
            for (String key : this.params) {
                if (params.containsKey(key)) {
                    Object value = params.get(key);
                    if (value == null) {
                        throw new DataSourceParamVerifyException(String.format("%s value must not null", key));
                    }
                } else {
                    throw new DataSourceParamVerifyException(String.format("%s must not null", key));
                }
            }
        }
    }


    private List<Object> toParamArgs(Map<String, Object> params){
        List<Object> args = new ArrayList<>();
        if (this.params != null) {
            for (String key : this.params) {
                args.add(params.get(key));
            }
        }
        return args;
    }


    public DataResult loadData(Map<String, Object> params) {
        this.verifyParams(params);
        return this.data(this.toParamArgs(params));
    }
}
