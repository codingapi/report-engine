package com.example.report.model.grid;

import com.example.report.model.source.FieldRef;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 小计/合计行：在分组断点处插入的汇总行。
 *
 * <ul>
 *   <li>{@link #groupBy} 指向某个分组列的字段 → <b>每个该分组结束后</b>插入一行小计
 *       （如"单位"结束后出"单位小计"）。</li>
 *   <li>{@link #groupBy} 为 null → <b>总计</b>，全表末尾插入一行。</li>
 * </ul>
 *
 * <p>行的位置随数据量自适应：小计/总计行会把它后面的内容整体下推。
 */
@Data
@Builder
public class SummaryRow {
    /** 触发分组的字段；null 表示总计（全表末尾一次） */
    private FieldRef groupBy;
    /** 该汇总行的单元格（标签 + 聚合） */
    private List<SummaryCell> cells;
}
