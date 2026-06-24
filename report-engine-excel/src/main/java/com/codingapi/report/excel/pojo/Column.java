package com.codingapi.report.excel.pojo;

import lombok.Data;

/**
 * 自定义列配置模型，对应前端 ExcelColumn 快照结构。
 *
 * <p>仅需要自定义列宽或隐藏状态的列才需要出现在 Sheet.columns 列表中， 未列出的列使用 Sheet.defaultColumnWidth 作为列宽且默认可见。
 */
@Data
public class Column {

    /** 列索引（0-based） */
    private int index;

    /** 列宽（像素），覆盖工作表的 defaultColumnWidth */
    private double width;

    /** 是否隐藏该列。隐藏后列宽视觉上为 0，但数据仍然存在 */
    private boolean hidden;
}
