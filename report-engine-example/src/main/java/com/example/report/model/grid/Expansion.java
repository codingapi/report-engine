package com.example.report.model.grid;

/**
 * 扩展方向 = 数据在格子上的铺开方式。这是类 Excel 报表的<b>本质机制</b>：
 * 报表里没有刚性的"表"对象，结构由每个格子的"扩展方向 + 父格"涌现出来。
 *
 * <ul>
 *   <li>{@link #VERTICAL}：纵向扩展（<b>行关系</b>），一条记录一行 —— 明细列表</li>
 *   <li>{@link #HORIZONTAL}：横向扩展（<b>列关系</b>），一条记录一列 —— 动态月份/指标列</li>
 *   <li>{@link #NONE}：不扩展，通常配合聚合取单值</li>
 * </ul>
 *
 * <p>行 + 列两个方向<b>正交组合即交叉表（矩阵报表）</b>：一个字段纵向扩展当行维度，
 * 一个字段横向扩展当列维度，交叉格放聚合值。
 */
public enum Expansion {
    VERTICAL, HORIZONTAL, NONE
}
