package com.codingapi.report.data;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class DataRow {

    private List<DataItem> items;

    public DataRow() {
        this.items = new ArrayList<>();
    }

    public void addItem(String name, Object value) {
        this.items.add(new DataItem(name, value));
    }


}
