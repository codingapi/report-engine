package com.codingapi.report.excel.pojo;

import lombok.Data;

/**
 * Excel 字体样式模型，对应前端 ExcelFont 快照结构。
 * <p>
 * 所有字段均为可选（null 表示不覆盖默认字体设置）。
 * 可直接映射到 Apache POI 的 Font API。
 * </p>
 */
@Data
public class Font {

    /** 字体族名称，如 "Arial"、"Microsoft YaHei"、"Times New Roman" */
    private String family;

    /** 字体大小（磅值，pt），如 12、14、18 */
    private Double size;

    /** 是否粗体 */
    private Boolean bold;

    /** 是否斜体 */
    private Boolean italic;

    /** 是否下划线 */
    private Boolean underline;

    /** 是否删除线 */
    private Boolean strikethrough;

    /** 字体颜色，格式为 #RRGGBB（如 "#FF0000" 表示红色） */
    private String color;
}
