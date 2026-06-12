package com.codingapi.report.excel.pojo;

import lombok.Data;

/**
 * 内边距配置模型，对应前端 ExcelStyle.padding 快照结构。
 * <p>
 * 单位为像素。在 Excel 中，仅 left（左侧）可近似映射为单元格缩进（indent），
 * 其余三边（top/right/bottom）Excel 原生不支持像素级内边距控制。
 * </p>
 */
@Data
public class Padding {

    /** 上内边距（像素），Excel 中无直接对应属性 */
    private Double top;

    /** 右内边距（像素），Excel 中无直接对应属性 */
    private Double right;

    /** 下内边距（像素），Excel 中无直接对应属性 */
    private Double bottom;

    /** 左内边距（像素），可近似映射为 Excel 单元格缩进（indent），约 7px 为一个缩进单位 */
    private Double left;
}
