package com.codingapi.report.components.table;

import com.codingapi.report.components.IComponent;
import com.codingapi.report.content.IContent;
import com.codingapi.report.stype.Style;

import java.util.List;

public class TableBody {

    /**
     * 样式
     */
    private Style style;

    /**
     * 表头
     */
    private List<IComponent> title;

    /**
     * 数据
     */
    private List<IContent> data;

}
