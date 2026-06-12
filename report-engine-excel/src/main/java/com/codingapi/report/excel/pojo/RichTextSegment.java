package com.codingapi.report.excel.pojo;

import lombok.Data;

/**
 * 富文本分段模型，对应前端 ExcelRichText 的 segments 元素。
 * <p>
 * 每个分段包含一段文本和可选的字体样式。
 * 所有分段的 text 按顺序拼接应等于 RichText.text 的完整内容。
 * </p>
 */
@Data
public class RichTextSegment {

    /** 该片段的文本内容 */
    private String text;

    /** 该片段的字体样式，为 null 时使用单元格默认字体 */
    private Font style;
}
