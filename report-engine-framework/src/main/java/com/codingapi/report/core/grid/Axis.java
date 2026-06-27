package com.codingapi.report.core.grid;

import com.codingapi.report.excel.pojo.Merge;

/**
 * 渲染轴：带（数据/汇总）在格子上推进的方向。纵向带与横向带互为转置—— 同一套带渲染逻辑通过 {@code Axis} 在"主轴 + 交叉轴"两个抽象坐标上运作， 不再硬编码行/列。
 *
 * <h3>主轴 / 交叉轴</h3>
 *
 * <ul>
 *   <li><b>主轴</b>：带推进的方向。纵向带沿<b>行</b>向下、横向带沿<b>列</b>向右。 记录依次落在 {@code 主轴基准 + 偏移} 上。
 *   <li><b>交叉轴</b>：与主轴正交的方向。纵向带的交叉坐标是<b>列</b>、横向带是<b>行</b>。 同一条记录的各个字段格分布在不同的交叉坐标上。
 * </ul>
 *
 * <p>汇总（{@link SummaryRow}）同样以此抽象表达：{@code mainPos} 是汇总声明的主轴位置、 {@code crossFrom/crossTo}
 * 是它覆盖的交叉区间、各 {@link SummaryCell} 的 {@code crossPos} 是其落在交叉轴上的坐标。纵向汇总在带下方追加一行、横向汇总在带右侧追加一列。
 */
public enum Axis {
    VERTICAL,
    HORIZONTAL;

    /** 交叉坐标：纵向带取声明格的列，横向带取声明格的行 */
    public int cross(CellRef c) {
        return this == VERTICAL ? c.column() : c.row();
    }

    /** 主轴基准：纵向带取声明格的行（带起始行），横向带取声明格的列（带起始列） */
    public int base(CellRef c) {
        return this == VERTICAL ? c.row() : c.column();
    }

    /** 落格行号：纵向 = 主轴基准+偏移，横向 = 交叉坐标 */
    public int outRow(int primaryBase, int offset, int cross) {
        return this == VERTICAL ? primaryBase + offset : cross;
    }

    /** 落格列号：纵向 = 交叉坐标，横向 = 主轴基准+偏移 */
    public int outCol(int primaryBase, int offset, int cross) {
        return this == VERTICAL ? cross : primaryBase + offset;
    }

    /** 构造合并区：纵向沿行跨 span 格（同列），横向沿列跨 span 格（同行） */
    public Merge merge(int primaryBase, int offset, int cross, int span) {
        Merge m = new Merge();
        if (this == VERTICAL) {
            m.setStartRow(primaryBase + offset);
            m.setStartCol(cross);
            m.setRowSpan(span);
            m.setColSpan(1);
        } else {
            m.setStartRow(cross);
            m.setStartCol(primaryBase + offset);
            m.setRowSpan(1);
            m.setColSpan(span);
        }
        return m;
    }
}
