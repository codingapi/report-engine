package com.codingapi.report.expression;

import com.codingapi.report.param.ParamContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/**
 * 表达式求值上下文：一次求值能看到的运行时数据。
 *
 * <h3>标量 vs 聚合两种作用域</h3>
 *
 * <ul>
 *   <li>{@link #row} — 当前行（标量字段读取用）。明细带的每一行、循环的每次迭代都对应一个当前行。
 *   <li>{@link #rows} — 行集合（聚合用）。{@link Value.Aggregate} 在这组行上汇总。
 * </ul>
 *
 * 两者可同时存在（聚合表达式里又嵌标量子表达式），也可只有其一。
 *
 * <p>{@link #params} 承载报表参数与循环作用域，供 {@link Value.ParamValue}/{@link Value.LoopFieldValue} 取值。
 */
@Getter
public class EvalContext {

    /** 当前行（限定列名 → 值），标量字段读取用，可为 null。 */
    private final Map<String, Object> row;

    /** 行集合，聚合用，可为 null。 */
    private final List<Map<String, Object>> rows;

    /** 参数上下文（报表参数 + 循环作用域）。 */
    private final ParamContext params;

    /** 渲染期注入的局部名（如小计行的 {@code group} = 当前分组值），{@link Value.NameRef} 优先查这里。 始终非 null。 */
    private final Map<String, Object> locals;

    public EvalContext(
            Map<String, Object> row,
            List<Map<String, Object>> rows,
            ParamContext params,
            Map<String, Object> locals) {
        this.row = row;
        this.rows = rows;
        this.params = params;
        this.locals = locals == null ? Map.of() : locals;
    }

    public EvalContext(
            Map<String, Object> row, List<Map<String, Object>> rows, ParamContext params) {
        this(row, rows, params, Map.of());
    }

    /** 标量上下文：只有当前行。 */
    public static EvalContext scalar(Map<String, Object> row, ParamContext params) {
        return new EvalContext(row, null, params);
    }

    /** 聚合上下文：只有行集合。 */
    public static EvalContext aggregate(List<Map<String, Object>> rows, ParamContext params) {
        return new EvalContext(null, rows, params);
    }

    /** 派生一个只换当前行、其余不变的上下文（聚合时逐行求子表达式用）。 */
    public EvalContext withRow(Map<String, Object> row) {
        return new EvalContext(row, this.rows, this.params, this.locals);
    }

    /** 派生一个追加一个局部名的上下文（如小计行注入 {@code group}）。 */
    public EvalContext withLocal(String name, Object value) {
        Map<String, Object> merged = new HashMap<>(locals);
        merged.put(name, value);
        return new EvalContext(row, rows, params, merged);
    }
}
