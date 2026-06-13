package com.example.report.engine;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 内存表：提取后、加工中流转的规整二维数据。
 *
 * <p>列名一律用<b>限定名</b> {@code datasetId.field}，避免多表 join 后字段冲突。
 * 行用 {@code Map<限定列名, 值>} 表示（最小引擎图省事；性能版可换列存 + 行下标）。
 * 值已按字段 {@code DataType} 归一化：NUMBER→Double、BOOLEAN→Boolean、其余→String。
 */
@Getter
@RequiredArgsConstructor
public class RawTable {
    private final List<String> columns;
    private final List<Map<String, Object>> rows;
}
