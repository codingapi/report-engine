package com.example.report.engine;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 内存表：数据提取后、加工中流转的<b>统一中间格式</b>。
 *
 * <h3>在数据流中的位置</h3>
 * <pre>
 *   DataExtractor.extract() → RawTable → Operators.filter/join/aggregate → ReportRenderer
 * </pre>
 * RawTable 是提取与加工之间的"契约"——所有提取器都输出 RawTable，所有算子都接收 RawTable，
 * 两侧互不感知对方的实现细节。
 *
 * <h3>列名约定：限定名 {@code datasetId.field}</h3>
 * <p>所有列名都用限定名格式（如 {@code employees.name}、{@code salaries.base}），
 * 而非裸字段名（{@code name}、{@code base}）。原因：
 * <ul>
 *   <li>多表 join 后可能有两个表都有 {@code name} 字段，限定名避免冲突</li>
 *   <li>{@link com.example.report.model.source.FieldRef} 天然就是 (datasetId, field)，
 *       限定名 = {@code fieldRef.datasetId() + "." + fieldRef.field()}，查找时一步到位</li>
 * </ul>
 *
 * <h3>行格式：{@code Map<String, Object>}</h3>
 * <p>每行是一个 Map，key 是限定列名，value 是归一化后的值：
 * <ul>
 *   <li>NUMBER → {@code Double}（所有数值统一为 Double，方便比较和聚合）</li>
 *   <li>BOOLEAN → {@code Boolean}</li>
 *   <li>其余（STRING/DATE/DATETIME/JSON）→ {@code String}</li>
 * </ul>
 *
 * <h3>性能考量</h3>
 * <p>当前最小实现用 {@code List<Map>}（行存），简单直观。
 * 如果数据量很大，可以换为列存（{@code Map<String, List<Object>>}）+ 行下标，
 * 对聚合操作更友好，但不影响接口契约。
 */
@Getter
@RequiredArgsConstructor
public class RawTable {
    /** 列名列表（限定名格式：datasetId.field），定义表的 schema */
    private final List<String> columns;
    /** 行数据列表，每行为一个 Map（key=限定列名，value=归一化值） */
    private final List<Map<String, Object>> rows;
}
