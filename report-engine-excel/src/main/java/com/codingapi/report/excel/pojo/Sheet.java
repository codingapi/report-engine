package com.codingapi.report.excel.pojo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;

/**
 * Excel 工作表模型，对应前端 ExcelSheet 快照结构。
 * <p>
 * 一个工作表包含单元格数据、合并区域、自定义行高列宽以及循环块定义。
 * 行列索引均为 0-based。
 * </p>
 */
@Data
public class Sheet {

    /** 工作表唯一标识（前端生成，如 UUID） */
    private String id;

    /** 工作表名称，显示在 Excel 底部标签页上 */
    private String name;

    /** 总行数（含空行），用于初始化工作表尺寸 */
    private int rowCount;

    /** 总列数（含空列），用于初始化工作表尺寸 */
    private int columnCount;

    /** 默认行高（像素），未单独设置行高的行使用此值，默认 24px */
    private double defaultRowHeight = 24;

    /** 默认列宽（像素），未单独设置列宽的列使用此值，默认 88px */
    private double defaultColumnWidth = 88;

    /** 合并区域列表 */
    private List<Merge> merges;

    /** 单元格数据列表，仅包含有内容或有样式的单元格（稀疏存储） */
    private List<Cell> cells;

    /** 自定义行高和隐藏状态的行配置列表 */
    private List<Row> rows;

    /** 自定义列宽和隐藏状态的列配置列表 */
    private List<Column> columns;

    /**
     * 循环块列表（报表模板概念）。
     * 导出时接受但构建 Excel 时忽略，仅用于前端快照的 round-trip 保持。
     */
    private List<JsonNode> loopBlocks;
}
