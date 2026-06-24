package com.codingapi.report.operator.condition;

import com.codingapi.report.expression.Value;
import lombok.Builder;
import lombok.Data;

/**
 * 过滤条件：{@code left operator right}，用于格子绑定和 Query 的数据筛选。
 *
 * <h3>使用位置</h3>
 *
 * <ul>
 *   <li>{@code CellBinding.getConditions()} — 格子级别的过滤，在渲染阶段生效，只影响该格子的数据范围
 *   <li>{@link com.codingapi.report.data.dataset.Query#getFilters()} — 查询级别的过滤，
 *       在数据提取阶段生效，影响整个数据集的行数
 * </ul>
 *
 * <h3>左右值都是 {@link Value} 表达式</h3>
 *
 * <p>条件两端统一用值表达式树，运行期由 {@code ExpressionEngine} 求值后交给 {@link ConditionPredicate} 比较。这样左值不限于裸字段（可写
 * {@code base+bonus}）， 右值天然支持字面量 / 报表参数 / 循环字段：
 *
 * <pre>
 *   gender = "男"               left=FieldValue(gender)   right=Literal("男")
 *   dept_id = :deptId          left=FieldValue(dept_id)  right=ParamValue("deptId")
 *   emp_id = loop.id           left=FieldValue(emp_id)   right=LoopFieldValue("loop1","id")
 * </pre>
 *
 * <h3>IS_NULL / IS_NOT_NULL 的特殊性</h3>
 *
 * <p>这两个算子不需要右值（field IS NULL 是完整的表达式），此时 {@link #right} 可为 null 或被忽略。
 */
@Data
@Builder
public class Condition {

    /** 左值表达式（通常是 {@link Value.FieldValue}，也可是算术等更复杂表达式）。 */
    private Value left;

    /** 比较算子（EQ/NE/GT/CONTAINS/IN/IS_NULL 等，见 {@link CompareOperator}）。 */
    private CompareOperator operator;

    /**
     * 右值表达式：字面量 / 报表参数 / 循环字段等。
     *
     * <p>IS_NULL/IS_NOT_NULL 算子时可为 null。
     */
    private Value right;
}
