package com.codingapi.report.model.param;

/**
 * 条件右值的来源（密封接口）：把"过滤条件的值"从只能填字面量，
 * 扩展成也能引用报表参数或循环字段。
 *
 * <h3>解决的核心问题</h3>
 * <p>如果条件右值只能是字面量，那过滤条件就是静态的——{@code gender = "男"} 永远只看男性。
 * ValueRef 让右值动态化：
 * <pre>
 *   字面量：  gender = "男"                    → 永远过滤男性
 *   参数引用：dept_id = :deptId                → 运行时传入哪个部门就看哪个部门
 *   循环字段：emp_id = loop_employees.id       → 循环到哪个员工就看哪个员工
 * </pre>
 *
 * <h3>三种来源详解</h3>
 *
 * <p><b>{@link Literal}</b>（字面量）：写死的固定值。
 * 配置时给什么就是什么，运行时不变。适用于不需要动态化的简单条件。
 *
 * <p><b>{@link Param}</b>（报表参数引用）：指向 {@link Parameter#getName()}。
 * 运行时引擎按参数的 {@link ParamSource} 求值：
 * <ul>
 *   <li>External → 从调用方传入的值中取</li>
 *   <li>Cell → 从指定格子的当前值取</li>
 *   <li>Constant → 从固定常量取</li>
 * </ul>
 *
 * <p><b>{@link LoopField}</b>（循环迭代字段引用）：引用某个循环块当前迭代行的字段。
 * <ul>
 *   <li><b>免登记</b>：不需要在 {@code Report.parameters} 里声明，
 *       只要格子在某循环块范围内，就能引用该循环驱动数据集的字段</li>
 *   <li><b>可发现性</b>：属性面板枚举可选值时，沿作用域链收集——
 *       报表参数 ∪ 各祖先循环驱动数据集的字段</li>
 *   <li><b>作用域</b>：{@code loopBlockId} 明确指定从哪个循环取，支持嵌套循环</li>
 * </ul>
 *
 * <h3>与 ParamSource 的关系</h3>
 * <p>ParamSource 是参数"从哪取值"的配置（参数定义侧），
 * ValueRef 是条件"引用什么"的配置（条件使用侧）。
 * 一个 ParamSource.External 参数可以被多个 ValueRef.Param 引用。
 *
 * <h3>为什么用密封接口？</h3>
 * <p>条件右值只有这三种来源（穷尽）。密封接口 + record 让编译器检查完整性：
 * 引擎的 {@code ParamContext.resolve(ValueRef)} 必须处理全部三种情况，
 * 新增类型时所有未处理的 switch 都会编译报错。
 */
public sealed interface ValueRef
        permits ValueRef.Literal, ValueRef.Param, ValueRef.LoopField {

    /**
     * 字面量：写死的固定值，运行时不变。
     *
     * @param value 固定值（类型应与条件左值字段的 DataType 匹配）
     */
    record Literal(Object value) implements ValueRef {
    }

    /**
     * 报表参数引用：指向 {@link Parameter#getName()}，运行时按参数的 ParamSource 求值。
     * <p>同一个参数可以被多个条件的 ValueRef.Param 引用。
     *
     * @param name 参数名（对应 Parameter.name，如 "deptId"）
     */
    record Param(String name) implements ValueRef {
    }

    /**
     * 循环当前迭代行的字段引用——<b>免预先登记</b>为 Parameter。
     *
     * <p>在某循环块范围内的格子，可直接引用该循环及其祖先循环"驱动数据集的字段"。
     * 例：薪资条循环（驱动数据集 = employees）内，格子条件 {@code emp_id = loop_emp.id}
     * 就是 {@code LoopField("loop_emp", "id")}。
     *
     * <p>渲染时，引擎在 {@code ParamContext} 的循环作用域中查找当前迭代行的对应字段值。
     *
     * @param loopBlockId 提供值的循环块 id（指向 {@link com.codingapi.report.model.grid.LoopBlock#getId()}）
     * @param field       取驱动数据集当前行的哪个字段（字段名，对应
     *                    {@link com.codingapi.report.model.source.Field#getName()}）
     */
    record LoopField(String loopBlockId, String field) implements ValueRef {
    }
}
