package com.codingapi.report.excel.pojo;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.Data;

/**
 * Excel 单元格模型，对应前端 ExcelCell 快照结构。
 *
 * <p>单元格通过 (row, col) 定位，支持多种值类型（字符串、数字、布尔、空值）， 同时可携带公式、富文本、样式和领域属性绑定。
 */
@Data
public class Cell {

    /** 行索引（0-based） */
    private int row;

    /** 列索引（0-based） */
    private int col;

    /** A1 表示法（如 "B3"），用于人类可读的定位标识 */
    private String ref;

    /**
     * 单元格值，支持多态类型：
     *
     * <ul>
     *   <li>字符串 — JSON text 节点
     *   <li>数字 — JSON number 节点（int/double）
     *   <li>布尔 — JSON boolean 节点
     *   <li>空值 — JSON null 或缺失
     * </ul>
     *
     * 使用 Jackson JsonNode 以保留原始类型信息。
     */
    private JsonNode value;

    /** 公式字符串（不含 = 前缀），如 "SUM(A1:A10)" */
    private String formula;

    /** 富文本内容，包含分段样式信息。存在时覆盖 value 的显示 */
    private RichText richText;

    /** 单元格样式（字体、对齐、边框、填充等） */
    private Style style;

    /** 领域属性绑定列表（如字段绑定、数据配置等）。 Excel 构建时忽略，仅用于前端快照的 round-trip 保持。 */
    private List<JsonNode> props;
}
