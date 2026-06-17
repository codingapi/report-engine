package com.codingapi.report.param;

import com.codingapi.report.data.dataset.DataType;
import lombok.Builder;
import lombok.Data;

/**
 * 报表参数：被条件以 {@code :name} 引用的具名值，登记在 {@code Report.parameters}。
 *
 * <h3>什么是报表参数？</h3>
 * <p>报表参数是"渲染时才能确定值"的变量。例如一张"部门报表"，
 * 模板是固定的，但每次运行时传入不同的 {@code deptId} 就能看到不同部门的数据。
 * 参数就是这个 {@code deptId} 的声明。
 *
 * <h3>参数的三种来源</h3>
 * <p>详见 {@link ParamSource}：
 * <ul>
 *   <li><b>External</b>（外部传入）：最常见。配置时不给值，运行时由调用方传入。
 *       所有 External 参数合起来 = 这张报表的"运行时输入契约"</li>
 *   <li><b>Cell</b>（单元格联动）：值由某个格子的当前值提供，用于格与格之间的联动</li>
 *   <li><b>Constant</b>（固定值）：固定不变的值，用于把魔法值显式命名化</li>
 * </ul>
 *
 * <h3>循环字段不是参数</h3>
 * <p>循环块的迭代值（如"当前员工的 id"）通过 {@link com.codingapi.report.expression.Value.LoopFieldValue} 直接引用，
 * 无需在 Parameter 列表里登记。这避免了 Parameter 列表被循环变量污染。
 * <p>两者在作用域链中共同构成可引用的取值来源：
 * <pre>
 *   报表作用域              { deptId (External 参数), year (External 参数) }
 *     └─ 循环块 A 作用域     { loopA.当前行.* = 驱动数据集 A 的字段 }
 *       └─ 循环块 B 作用域   { loopB.当前行.* = 驱动数据集 B 的字段 }
 * </pre>
 * 查找顺序：内层循环字段 → 外层循环字段 → 报表参数（就近优先）。
 *
 * <h3>前端如何使用？</h3>
 * <p>参数面板根据 {@code Report.parameters} 中 {@code ParamSource.External} 的参数
 * 动态生成输入表单：{@link #dataType} 决定输入控件类型（NUMBER → 数字框，DATE → 日期选择器），
 * {@link ParamSource.External#required()} 决定是否必填，
 * {@link ParamSource.External#defaultValue()} 提供默认值。
 */
@Data
@Builder
public class Parameter {
    /** 参数名，被 {@link com.codingapi.report.expression.Value.ParamValue} 引用（如 {@code :deptId}） */
    private String name;
    /** 别名/中文名称（可选），用于前端参数面板和表达式选择界面的友好展示 */
    private String alias;
    /** 数据类型，决定前端参数面板的输入控件和后端值转换 */
    private DataType dataType;
    /** 值的来源（External/Cell/Constant），决定何时、从哪取值 */
    private ParamSource source;
}
