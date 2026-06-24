package com.codingapi.report.excel.pojo;

import lombok.Data;

/**
 * Excel 单元格样式模型，对应前端 ExcelStyle 快照结构。
 *
 * <p>涵盖字体、对齐、自动换行、文字旋转、背景填充、四边边框、数字格式和内边距。 可直接映射到 Apache POI 的 CellStyle 和 Font API。
 */
@Data
public class Style {

    /** 字体配置（字族、字号、粗体、斜体、下划线、删除线、颜色） */
    private Font font;

    /** 水平对齐方式。 可选值：left / center / right / justify / distributed */
    private String align;

    /** 垂直对齐方式。 可选值：top / middle / bottom */
    private String valign;

    /** 是否自动换行。为 true 时长文本会在单元格宽度边界处折行 */
    private Boolean wrap;

    /** 文字旋转角度（0-180 度），0 表示水平显示 */
    private Integer rotation;

    /** 背景填充色，格式为 #RRGGBB（如 "#1F4E79"） */
    private String fill;

    /** 四边边框配置（上、右、下、左，各边可独立设置线型和颜色） */
    private Borders borders;

    /** 数字格式字符串，遵循 Excel 格式规范。 例如："0.00"（两位小数）、"#,##0"（千位分隔）、"yyyy-MM-dd"（日期） */
    private String numberFormat;

    /** 内边距配置（像素），Excel 中仅 left 可近似映射为缩进（indent） */
    private Padding padding;
}
