package com.codingapi.report.engine;

import com.codingapi.report.model.param.ValueRef;

import java.util.HashMap;
import java.util.Map;

/**
 * 参数上下文：渲染时求解条件右值（{@link ValueRef}）和文本占位（{@code ${name}}）的运行时环境。
 *
 * <h3>两层作用域</h3>
 * <pre>
 *   ParamContext
 *   ├── external（报表级）        ← 构造时传入，作用域 = 整张报表
 *   │   例：{ deptId: 5, year: 2026 }
 *   │
 *   └── loopRows（循环级）        ← 渲染循环块时动态设置
 *       ├── "loop_emp" → { id: 1, name: "张三", dept: "研发" }
 *       └── "loop_dept" → { deptId: 5, deptName: "研发中心" }
 * </pre>
 * <p>{@code external} 是运行时传入的报表参数值（调用方在 {@code render()} 时提供）。
 * {@code loopRows} 承载各循环块当前迭代行的字段值，
 * 由 {@link ReportRenderer} 在遍历循环时通过 {@link #setLoopRow} 动态更新。
 *
 * <h3>两个求解方法的区别</h3>
 * <ul>
 *   <li>{@link #resolve(ValueRef)} — 求解条件右值（{@link ValueRef} 的三种实现）。
 *       精确匹配 ValueRef 的类型分发：Literal 取字面量，Param 查 external，LoopField 查 loopRows</li>
 *   <li>{@link #lookup(String)} — 求解文本占位 {@code ${name}}。
 *       按名字模糊查找：先遍历所有循环作用域（内层优先），再查 external。
 *       这样 {@code "${name}的薪资"} 在循环块内取当前员工的 name，
 *       在块外则查报表参数里有没有叫 name 的参数</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * <p>非线程安全。每次 {@code render()} 调用创建一个实例，渲染完毕即可丢弃。
 * 循环迭代时 {@code loopRows} 被反复覆盖（每次迭代更新当前行），
 * 但同一时刻只有一个迭代在进行，不需要并发保护。
 */
public class ParamContext {

    /** 报表级外部参数（构造时传入，渲染期间不变） */
    private final Map<String, Object> external;

    /**
     * 循环块当前迭代行（动态更新）。
     * <p>key = 循环块 id，value = 该循环当前迭代行的"字段名 → 值"（不带 datasetId 前缀，
     * 因为循环作用域里字段名天然唯一——同一循环只引用一个驱动数据集）。
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
     * <ol>
     *   <li>遍历所有活跃的循环作用域，找到包含该字段名的第一个</li>
     *   <li>所有循环作用域都没有 → 查 external 报表参数</li>
     * </ol>
     *
     * <p>示例：
     * <ul>
     *   <li>{@code "${name}的薪资"} — 在循环块内，name 取当前迭代员工的 name 字段</li>
     *   <li>{@code "${year}年度报表"} — year 不在循环作用域，取 external 的 year 参数</li>
     * </ul>
     *
     * <p>注：当多个循环作用域都有同名时，取先遍历到的（HashMap 无序）。
     * 实际使用中，嵌套循环通常不会出现同名字段冲突。
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
     * <p>由 ReportRenderer 在每次循环迭代开始时调用。
     *
     * @param loopBlockId 循环块 id
     * @param row         当前迭代行的"字段名 → 值"（不带 datasetId 前缀，
     *                    由 {@code unqualify()} 去除限定名前缀后传入）
     */
    public void setLoopRow(String loopBlockId, Map<String, Object> row) {
        loopRows.put(loopBlockId, row);
    }

    /**
     * 求解条件右值（{@link ValueRef} 的三种实现分发）。
     *
     * <ul>
     *   <li>{@link ValueRef.Literal} → 直接返回字面量值</li>
     *   <li>{@link ValueRef.Param} → 从 external 报表参数中按名字查找</li>
     *   <li>{@link ValueRef.LoopField} → 从指定循环块的当前迭代行中取字段值</li>
     * </ul>
     *
     * @param ref 条件右值引用
     * @return 求解后的实际值，找不到返回 null
     */
    public Object resolve(ValueRef ref) {
        if (ref instanceof ValueRef.Literal l) {
            return l.value();
        }
        if (ref instanceof ValueRef.Param p) {
            return external.get(p.name());
        }
        if (ref instanceof ValueRef.LoopField lf) {
            Map<String, Object> row = loopRows.get(lf.loopBlockId());
            return row == null ? null : row.get(lf.field());
        }
        return null;
    }
}
