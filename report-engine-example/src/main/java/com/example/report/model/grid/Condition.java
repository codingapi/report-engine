package com.example.report.model.grid;

import com.example.report.model.param.ValueRef;
import com.example.report.model.source.FieldRef;
import lombok.Builder;
import lombok.Data;

/**
 * 过滤条件：{@code left  operator  value}。
 *
 * <p>右值 {@link #value} 是关键扩展点——它可以是字面量，也可以是<b>参数引用</b>：
 * <pre>
 *   部门报表：dept_id = :deptId    （value = ValueRef.Param("deptId")，运行时传入）
 *   薪资条：  emp_id  = :empId     （value = ValueRef.Param("empId")，循环逐迭代提供）
 * </pre>
 */
@Data
@Builder
public class Condition {
    /** 左值：参与过滤的字段 */
    private FieldRef left;
    private CompareOperator operator;
    /** 右值：字面量或参数引用 */
    private ValueRef value;
}
