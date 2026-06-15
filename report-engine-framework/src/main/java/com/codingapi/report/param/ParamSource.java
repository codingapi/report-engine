package com.codingapi.report.param;

import com.codingapi.report.render.grid.CellRef;

/**
 * 参数来源（密封接口）：决定一个 {@link Parameter} 的"值从哪来、什么时候绑定"。
 *
 * <h3>三种来源的设计动机</h3>
 *
 * <p><b>{@link External}（外部传入）</b>— 最常用。报表的"运行时输入"。
 * <ul>
 *   <li>配置时只声明参数存在（名称、类型、是否必填、默认值），不给具体值</li>
 *   <li>运行时由调用方传入：{@code render(reportId, {deptId: 5, year: 2026})}</li>
 *   <li>所有 External 参数合起来 = 这张报表的"输入契约"，也是前端参数面板的渲染依据</li>
 *   <li>例：部门报表的 deptId，月度报表的 year/month</li>
 * </ul>
 *
 * <p><b>{@link Cell}（单元格联动）</b>— 格与格之间的动态关联。
 * <ul>
 *   <li>参数值由另一个格子的当前值提供</li>
 *   <li>典型场景：用户在"部门"格子选了一个值，"部门名称"参数自动跟随更新</li>
 *   <li>实现了"报表内部交互"而不需要外部重新传参</li>
 * </ul>
 *
 * <p><b>{@link Constant}（固定值）</b>— 把魔法值显式命名化。
 * <ul>
 *   <li>值在设计时就确定了，不会变化</li>
 *   <li>看起来多余（直接写死不行吗？），但好处是：给固定值一个有意义的名字，
 *       让条件表达式更可读（{@code status = :ACTIVE_STATUS} 比 {@code status = 1} 清晰）</li>
 * </ul>
 *
 * <h3>为什么用密封接口？</h3>
 * <p>参数来源只有这三种（穷尽），密封接口 + record 实现让编译器帮助检查完整性：
 * 如果新增了一种来源，所有 switch/instanceof 的地方都会编译报错，强制处理新情况。
 *
 * <h3>与 ValueRef 的关系</h3>
 * <p>ParamSource 决定参数"从哪取值"（配置视角），
 * {@link ValueRef} 决定条件右值"引用什么"（使用视角）。
 * ParamSource.External 参数 → 被 ValueRef.Param 引用 → 运行时求值。
 */
public sealed interface ParamSource
        permits ParamSource.External, ParamSource.Cell, ParamSource.Constant {

    /**
     * 外部传入：运行时由调用方提供的显式参数。
     * <p>前端参数面板根据 {@code required} 和 {@code defaultValue} 生成对应的输入控件。
     * <p>渲染时绑定一次，作用域 = 整张报表。
     *
     * @param required     是否必填。true 时未传且无默认值则报错或弹参数面板要求输入
     * @param defaultValue 默认值，未传入时使用。可为 null（配合 required=false 表示可选无默认）
     */
    record External(boolean required, Object defaultValue) implements ParamSource {
    }

    /**
     * 单元格联动：值由模板中某个格子的当前值提供。
     * <p>实现了报表内部的动态联动——一个格子的变化可以驱动其他格子的过滤条件。
     *
     * @param cell 取值的源格子坐标（sheetId + row + column）
     */
    record Cell(CellRef cell) implements ParamSource {
    }

    /**
     * 固定值：设计时确定、运行时不变。
     * <p>用于把魔法值（如状态码 1="在职"）显式命名化，提高条件表达式的可读性。
     *
     * @param value 常量值（类型应与 {@link Parameter#getDataType()} 一致）
     */
    record Constant(Object value) implements ParamSource {
    }
}
