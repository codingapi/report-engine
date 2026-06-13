package com.example.report.model.grid;

/**
 * 扩展模式（仅当 {@link Expansion} 不为 NONE 时有意义）：
 *
 * <ul>
 *   <li>{@link #GROUP}：按值<b>去重分组</b>。用于多级分组统计表的分组列（单位、部门），
 *       常配合 {@code CellBinding.mergeRepeated} 把相邻相同值合并成跨行/跨列单元格。</li>
 *   <li>{@link #LIST}：<b>明细</b>，每行/每列都出，不去重。用于明细列表。</li>
 * </ul>
 */
public enum ExpandMode {
    GROUP, LIST
}
