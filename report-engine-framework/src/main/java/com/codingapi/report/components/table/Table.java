package com.codingapi.report.components.table;

import com.codingapi.report.components.IComponent;
import com.codingapi.report.stype.Style;
import lombok.Getter;
import lombok.Setter;

/**
 * 表格组件
 */
@Getter
@Setter
public class Table implements IComponent {
    /**
     * 表格头部
     */
    private TableHeader header;
    /**
     * 表格内容
     */
    private TableBody body;
    /**
     * 表格底部
     */
    private TableFooter footer;

    /**
     * 样式
     */
    private Style style;
}
