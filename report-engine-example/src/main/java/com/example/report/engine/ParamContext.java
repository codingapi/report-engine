package com.example.report.engine;

import com.example.report.model.param.ValueRef;

import java.util.HashMap;
import java.util.Map;

/**
 * 参数上下文：渲染时求解条件右值 / 文本占位。
 *
 * <p>{@code external} 是运行时传入的报表参数值（如 {@code deptId=5}）。
 * {@code loopRows} 承载循环块当前迭代行（key=循环块 id，value=该行的"字段名→值"），
 * 供 {@link ValueRef.LoopField} 求解——这就是循环把"当前迭代行"发布进作用域的实现。
 */
public class ParamContext {

    private final Map<String, Object> external;
    private final Map<String, Map<String, Object>> loopRows = new HashMap<>();

    public ParamContext(Map<String, Object> external) {
        this.external = external;
    }

    public Object external(String name) {
        return external.get(name);
    }

    /**
     * 文本占位 {@code ${name}} 求值：先查当前循环行（内层作用域），再查报表参数（外层）。
     * 于是 "${name}的薪资" 中的 name 取循环当前员工，"${year}报表" 中的 year 取外部参数。
     */
    public Object lookup(String name) {
        for (Map<String, Object> row : loopRows.values()) {
            if (row.containsKey(name)) {
                return row.get(name);
            }
        }
        return external.get(name);
    }

    /** 设置/更新某循环块当前迭代行（字段名→值，不带数据集前缀） */
    public void setLoopRow(String loopBlockId, Map<String, Object> row) {
        loopRows.put(loopBlockId, row);
    }

    /** 求解条件右值 */
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
