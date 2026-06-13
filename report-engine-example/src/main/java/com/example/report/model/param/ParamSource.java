package com.example.report.model.param;

import com.example.report.model.grid.CellRef;

/**
 * 参数来源：决定一个报表参数的"值从哪来、什么时候绑定"。
 *
 * <ul>
 *   <li>{@link External}：运行时由外部传入。渲染时绑定一次，作用域=整张报表。
 *       所有 External 参数合起来就是这张报表的"输入契约"（也是参数面板的渲染依据）。
 *       例：部门报表的 deptId，调用方 {@code render(reportId, {deptId:5})} 时给值。</li>
 *
 *   <li>{@link Cell}：由某个格子的当前值提供，用于单元格之间的联动。</li>
 *
 *   <li>{@link Constant}：固定值（少见，主要用于把魔法值显式化）。</li>
 * </ul>
 *
 * <p>注意：<b>循环块的迭代值不是参数</b>，不在这里——它通过
 * {@link ValueRef.LoopField} 直接引用循环驱动数据集的字段，免登记。
 * 这样 {@link Parameter} 注册表只保留真正的报表级输入，更干净。
 */
public sealed interface ParamSource
        permits ParamSource.External, ParamSource.Cell, ParamSource.Constant {

    /**
     * 外部传入（显式参数）。
     *
     * @param required     是否必填；未传且无默认值时报错或弹参数面板
     * @param defaultValue 默认值，可为 null
     */
    record External(boolean required, Object defaultValue) implements ParamSource {
    }

    /**
     * 某个格子的值（单元格联动）。
     *
     * @param cell 取值的源格子
     */
    record Cell(CellRef cell) implements ParamSource {
    }

    /**
     * 固定值。
     *
     * @param value 常量值
     */
    record Constant(Object value) implements ParamSource {
    }
}
