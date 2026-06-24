package com.codingapi.report.excel.pojo;

import java.util.List;
import lombok.Data;

/**
 * 富文本模型，对应前端 ExcelRichText 快照结构。
 *
 * <p>富文本允许在同一段文本中为不同片段应用不同的字体样式（如颜色、粗体、斜体等）。 对应 Apache POI 的 XSSFRichTextString。
 */
@Data
public class RichText {

    /** 完整的纯文本内容（所有分段文本拼接后的结果） */
    private String text;

    /** 分段样式列表，按顺序拼接后应与 text 字段内容一致。 每个分段包含一段文本和可选的字体样式。 */
    private List<RichTextSegment> segments;
}
