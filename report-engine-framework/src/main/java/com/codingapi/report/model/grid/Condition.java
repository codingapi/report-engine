package com.codingapi.report.model.grid;

import com.codingapi.report.model.param.ValueRef;
import com.codingapi.report.model.source.FieldRef;
import lombok.Builder;
import lombok.Data;

/**
 * 过滤条件：{@code left  operator  value}，用于 FieldCell 和 Query 的数据筛选。
 *
 * <h3>使用位置</h3>
 * <ul>
 *   <li>{@link FieldCell#getConditions()} — 格子级别的过滤，在渲染阶段生效，
 *       只影响该格子的数据范围</li>
 *   <li>{@link com.codingapi.report.model.source.Query#getFilters()} — 查询级别的过滤，
 *       在数据提取阶段生效，影响整个数据集的行数</li>
 * </ul>
 *
 * <h3>右值 {@link #value} 是关键的扩展点</h3>
 * <p>条件右值不是只能填固定值——通过 {@link com.codingapi.report.model.param.ValueRef}
 * 密封接口，支持三种来源：
 * <pre>
 *   1. 字面量（Literal）：
 *      gender = "男"                   → value = ValueRef.Literal("男")
 *
 *   2. 报表参数（Param）：
 *      dept_id = :deptId               → value = ValueRef.Param("deptId")
 *      运行时由调用方传入，同一张报表可以"传不同的部门看不同的数据"
 *
 *   3. 循环字段（LoopField）：
 *      emp_id = loop_employees.id      → value = ValueRef.LoopField("loop1", "id")
 *      在循环块内，每次迭代用当前行的 id 作为过滤值
 * </pre>
 *
 * <h3>IS_NULL / IS_NOT_NULL 的特殊性</h3>
 * <p>这两个算子不需要右值（field IS NULL 是完整的表达式），
 * 此时 {@link #value} 可为 null 或被引擎忽略。
 */
@Data
@Builder
public class Condition {
    /** 左值：参与过滤的字段引用（datasetId + 字段名） */
    private FieldRef left;

    /** 比较算子（EQ/NE/GT/LIKE/IN/BETWEEN 等 12 种） */
    private CompareOperator operator;

    /**
     * 右值：字面量、参数引用或循环字段引用。
     * <p>通过 {@link com.codingapi.report.model.param.ValueRef} 密封接口的三种实现，
     * 让过滤条件能静态配置、动态参数化、循环上下文感知。
     * <p>IS_NULL/IS_NOT_NULL 算子时可为 null。
     */
    private com.codingapi.report.model.param.ValueRef value;
}
