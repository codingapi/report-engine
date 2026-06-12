package com.codingapi.report.excel.pojo;

import lombok.Data;

/**
 * 单条边框定义，对应前端 ExcelBorder 快照结构。
 * <p>
 * 定义边框的线型和颜色，用于 Borders 中的某一侧。
 * 支持 13 种线型，直接映射到 Apache POI 的 BorderStyle 枚举。
 * </p>
 */
@Data
public class Border {

    /**
     * 边框线型，支持以下 13 种值（与 Apache POI BorderStyle 一一对应）：
     * <ul>
     *   <li>thin — 细实线</li>
     *   <li>hair — 极细线</li>
     *   <li>dotted — 点线</li>
     *   <li>dashed — 虚线</li>
     *   <li>dashDot — 点划线</li>
     *   <li>dashDotDot — 双点划线</li>
     *   <li>double — 双线</li>
     *   <li>medium — 中等粗线</li>
     *   <li>mediumDashed — 中等粗虚线</li>
     *   <li>mediumDashDot — 中等粗点划线</li>
     *   <li>mediumDashDotDot — 中等粗双点划线</li>
     *   <li>slantDashDot — 斜点划线</li>
     *   <li>thick — 粗实线</li>
     * </ul>
     */
    private String style;

    /** 边框颜色，格式为 #RRGGBB（如 "#000000" 表示黑色） */
    private String color;
}
