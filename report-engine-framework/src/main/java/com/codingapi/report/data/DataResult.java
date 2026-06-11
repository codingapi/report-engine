package com.codingapi.report.data;

import com.codingapi.report.meta.DBTable;
import lombok.Getter;

import java.util.List;

public class DataResult {

    @Getter
    private final List<DataRow> rows;

    public DataResult(List<DataRow> rows) {
        this.rows = rows;
    }

    public DBTable toTable(String name) {
        DBTable dbTable = new DBTable(name, null);
        if (rows != null && !rows.isEmpty()) {
            DataRow row = rows.get(0);

        }
        return dbTable;
    }

    public int size() {
        return this.rows.size();
    }
}
