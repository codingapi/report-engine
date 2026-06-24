package com.codingapi.report.param;

import java.util.HashMap;
import java.util.Map;

/**
 * 参数上下文：渲染时供表达式求值取"运行时值"的环境——报表参数（external）+ 循环作用域（loopRows）。
 *
 * <h3>两层作用域</h3>
 *
 * <pre>
 *   ParamContext
 *   ├── external（报表级）        ← 构造时传入，作用域 = 整张报表
 *   │   例：{ deptId: 5, year: 2026 }
 *   │
 *   └── loopRows（循环级）        ← 渲染循环块时动态设置
 *       ├── "loop_emp" → { id: 1, name: "张三", dept: "研发" }
 *       └── "loop_dept" → { deptId: 5, deptName: "研发中心" }
 * </pre>
 *
 * <p>{@code external} 是运行时传入的报表参数值（调用方在 {@code render()} 时提供）。 {@code loopRows} 承载各循环块当前迭代行的字段值，由
 * ReportRenderer 在遍历循环时通过 {@link #setLoopRow} 动态更新。
 *
 * <h3>取值方法</h3>
 *
 * <ul>
 *   <li>{@link #external(String)} — 取报表参数（{@code Value.ParamValue} 用）
 *   <li>{@link #loopField(String, String)} — 取某循环块当前行的字段（{@code Value.LoopFieldValue} 用）
 *   <li>{@link #lookup(String)} — 按名字就近查找：循环作用域优先、再报表参数（{@code Value.NameRef} 用， 即文本插值 {@code
 *       ${name}}）
 * </ul>
 *
 * <h3>线程安全</h3>
 *
 * <p>非线程安全。每次 {@code render()} 调用创建一个实例，渲染完毕即可丢弃。 循环迭代时 {@code loopRows} 被反复覆盖（每次迭代更新当前行），
 * 但同一时刻只有一个迭代在进行，不需要并发保护。
 */
public class ParamContext {

    /** 报表级外部参数（构造时传入，渲染期间不变） */
    private final Map<String, Object> external;

    /**
     * 循环块当前迭代行（动态更新）。
     *
     * <p>key = 循环块 id，value = 该循环当前迭代行的"字段名 → 值"（不带 datasetId 前缀， 因为循环作用域里字段名天然唯一——同一循环只引用一个驱动数据集）。
     */
    private final Map<String, Map<String, Object>> loopRows = new HashMap<>();

    public ParamContext(Map<String, Object> external) {
        this.external = external;
    }

    /** 直接取报表级参数值 */
    public Object external(String name) {
        return external.get(name);
    }

    /**
     * 文本占位 {@code ${name}} 求值：先查循环作用域（内层优先），再查报表参数（外层）。
     *
     * <p>查找顺序：
     *
     * <ol>
     *   <li>遍历所有活跃的循环作用域，找到包含该字段名的第一个
     *   <li>所有循环作用域都没有 → 查 external 报表参数
     * </ol>
     *
     * <p>示例：
     *
     * <ul>
     *   <li>{@code "${name}的薪资"} — 在循环块内，name 取当前迭代员工的 name 字段
     *   <li>{@code "${year}年度报表"} — year 不在循环作用域，取 external 的 year 参数
     * </ul>
     *
     * <p>注：当多个循环作用域都有同名时，取先遍历到的（HashMap 无序）。 实际使用中，嵌套循环通常不会出现同名字段冲突。
     */
    public Object lookup(String name) {
        for (Map<String, Object> row : loopRows.values()) {
            if (row.containsKey(name)) {
                return row.get(name);
            }
        }
        return external.get(name);
    }

    /**
     * 设置/更新某循环块当前迭代行。
     *
     * <p>由 ReportRenderer 在每次循环迭代开始时调用。
     *
     * @param loopBlockId 循环块 id
     * @param row 当前迭代行的"字段名 → 值"（不带 datasetId 前缀， 由 {@code unqualify()} 去除限定名前缀后传入）
     */
    public void setLoopRow(String loopBlockId, Map<String, Object> row) {
        loopRows.put(loopBlockId, row);
    }

    /**
     * 取某循环块当前迭代行的字段值（不带 datasetId 前缀）。
     *
     * <p>供表达式求值（{@code expression.Value.LoopFieldValue}）和 {@link #resolve} 复用。
     *
     * @return 该字段当前迭代值，循环未激活或字段不存在时返回 null
     */
    public Object loopField(String loopBlockId, String field) {
        Map<String, Object> row = loopRows.get(loopBlockId);
        return row == null ? null : row.get(field);
    }
}
