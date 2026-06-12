package com.codingapi.report.excel.pojo;

import lombok.Data;

/**
 * 字体信息模型，描述字体库中单个字体文件的元数据。
 * <p>
 * 由 {@link com.codingapi.report.excel.FontRegistry} 在扫描字体目录时生成，
 * 用于前端获取可用字体清单并按需加载。
 * </p>
 */
@Data
public class FontInfo {

    /**
     * 字体族名称（从字体文件元数据中解析），
     * 如 "Arial"、"微软雅黑"、"Times New Roman"。
     * 这是 Excel 和前端 CSS 中使用的标准字体名称。
     */
    private String family;

    /**
     * 字体样式变体。
     * 可选值：regular / bold / italic / bold-italic
     */
    private String style;

    /** 字体文件名（含扩展名），如 "arial.ttf"、"微软雅黑.ttf" */
    private String filename;

    /** 字体文件格式：ttf / otf / ttc */
    private String format;
}
