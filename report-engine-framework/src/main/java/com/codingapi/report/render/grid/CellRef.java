package com.codingapi.report.render.grid;

/**
 * 单元格坐标引用：定位模板工作簿中的一个具体格子。
 *
 * <h3>在模型中的使用场景</h3>
 * <ul>
 *   <li>{@link CellBinding#getCell()} — 每个格子绑定的目标坐标</li>
 *   <li>{@link CellBinding#getParentCell()} — 父格的坐标引用（串成父格链）</li>
 *   <li>{@link LoopBlock#getStart()} / {@link LoopBlock#getEnd()} — 循环区域的范围</li>
 *   <li>{@link com.codingapi.report.param.ParamSource.Cell} — 单元格联动的取值源</li>
 * </ul>
 *
 * <h3>为什么用 record 而非 class？</h3>
 * <p>CellRef 是纯值对象（value object），只有数据没有行为。使用 record 获得：
 * 自动生成 equals/hashCode（用于 Map key 和 Set 查找）、不可变性、简洁的构造语法。
 *
 * <h3>坐标约定</h3>
 * <p>row 和 column 从 0 开始，与 {@code report-engine-excel} 的 Workbook 模型保持一致。
 * sheetId 是字符串而非整数索引，因为 Univer 使用字符串 id 标识 sheet。
 *
 * @param sheetId 所在 sheet 的 id（Univer 使用字符串 id，如 "sheet-01"）
 * @param row     行号（0-based，与 Excel POI 一致）
 * @param column  列号（0-based，与 Excel POI 一致）
 */
public record CellRef(String sheetId, int row, int column) {
}
