package com.example.report.model.param;

import com.example.report.model.source.DataType;
import lombok.Builder;
import lombok.Data;

/**
 * 报表参数：被条件以 {@code :name} 引用的具名值，登记在 {@code Report.parameters}。
 *
 * <p>这里只保留<b>报表级输入</b>（来源见 {@link ParamSource}：External / Cell / Constant）。
 * 其中 External 参数合起来就是报表的运行时输入契约，例如部门报表的 deptId
 * （配置时不给值，运行时由调用方传入）。
 *
 * <p>循环块的迭代值<b>不是参数</b>——它由 {@link ValueRef.LoopField} 直接引用循环
 * 驱动数据集的字段，无需在此登记。两者在作用域里共同构成可引用的取值来源：
 * <pre>
 *   报表作用域            { deptId (External 参数) }
 *     └─ 循环块作用域      { loop.当前行.* = 驱动数据集字段 }  ← 经 LoopField 引用
 * </pre>
 */
@Data
@Builder
public class Parameter {
    /** 参数名，被 {@link ValueRef.Param} 引用 */
    private String name;
    private DataType dataType;
    /** 值的来源，决定何时、从哪取值 */
    private ParamSource source;
}
