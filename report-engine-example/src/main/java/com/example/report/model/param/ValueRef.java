package com.example.report.model.param;

/**
 * 条件右值的来源：把"过滤条件的值"从只能填字面量，扩展成也能引用参数或循环字段。
 *
 * <p>这是参数系统/循环上下文与属性面板的<b>联动接口</b>。
 */
public sealed interface ValueRef
        permits ValueRef.Literal, ValueRef.Param, ValueRef.LoopField {

    /**
     * 字面量：写死的固定值。
     *
     * @param value 值
     */
    record Literal(Object value) implements ValueRef {
    }

    /**
     * 报表参数引用，指向 {@link Parameter#getName()}，运行时按参数来源求值。
     *
     * @param name 参数名（:deptId）
     */
    record Param(String name) implements ValueRef {
    }

    /**
     * 循环当前迭代行的字段引用——<b>免预先登记</b>。
     *
     * <p>可发现性来自循环的驱动数据集字段：在某循环块内编辑的格子，可引用该循环及其
     * 祖先循环"驱动数据集的字段"。例：薪资条里 {@code emp_id = loop_emp.id}。
     *
     * @param loopBlockId 提供值的循环块 id
     * @param field       取驱动数据集当前行的哪个字段
     */
    record LoopField(String loopBlockId, String field) implements ValueRef {
    }
}
