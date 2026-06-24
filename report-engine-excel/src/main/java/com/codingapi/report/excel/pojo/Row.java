package com.codingapi.report.excel.pojo;

import lombok.Data;

/**
 * 自定义行配置模型，对应前端 ExcelRow 快照结构。
 *
 * <p>仅需要自定义行高或隐藏状态的行才需要出现在 Sheet.rows 列表中， 未列出的行使用 Sheet.defaultRowHeight 作为行高且默认可见。
 */
@Data
public class Row {

    /** 行索引（0-based） */
    private int index;

    /** 行高（像素），覆盖工作表的 defaultRowHeight */
    private double height;

    /** 是否隐藏该行。隐藏后行高视觉上为 0，但数据仍然存在 */
    private boolean hidden;
}
