package com.codingapi.report.render.grid;

import com.codingapi.report.param.ValueRef;
import com.codingapi.report.data.dataset.Query;
import lombok.Builder;
import lombok.Data;

/**
 * 循环块：带迭代上下文的扩展容器。
 *
 * <h3>典型场景</h3>
 * <p><b>薪资条</b>——模板定义一个人的薪资布局，循环 N 次就生成 N 个人的薪资条：
 * <pre>
 *   模板（一次定义）：
 *   ┌────────────────────────────┐
 *   │ 姓名: ${name}    部门: ${dept} │  ← TextCell 引用 LoopField
 *   │ 基本工资: ${base}  奖金: ${bonus}│  ← FieldCell 引用 LoopField
 *   │ 合计: ${total}                  │
 *   └────────────────────────────┘
 *
 *   渲染结果（N 个人循环展开）：
 *   ┌────────────────────────────┐
 *   │ 姓名: 张三    部门: 研发     │  ← 第 1 次迭代
 *   │ 基本工资: 8000  奖金: 2000   │
 *   │ 合计: 10000                  │
 *   ├────────────────────────────┤
 *   │ 姓名: 李四    部门: 测试     │  ← 第 2 次迭代
 *   │ 基本工资: 7000  奖金: 1500   │
 *   │ 合计: 8500                   │
 *   └────────────────────────────┘
 * </pre>
 *
 * <h3>驱动源是 Query，不是裸数据源</h3>
 * <p>{@link #source} 是一个 {@link Query}（数据集 + 过滤 + 分组），好处是：
 * <ul>
 *   <li>循环范围能被参数控制：{@code filters} 里可以写 {@code dept_id = :deptId}，
 *       运行时只循环指定部门的员工</li>
 *   <li>{@code groupBy} 决定"逐行迭代"（每人一次）还是"按分组去重迭代"（每部门一次）</li>
 * </ul>
 *
 * <h3>循环字段免登记</h3>
 * <p>循环把"当前迭代行"发布进作用域，块内格子通过
 * {@link ValueRef.LoopField}{@code (loopId, field)} 直接引用驱动数据集的字段，
 * 无需预先在 {@link com.codingapi.report.render.Report#getParameters()} 里登记。
 * <p>属性面板枚举可选值时，沿作用域链向上收集：
 * <pre>
 *   报表参数 ∪ 外层循环字段 ∪ 当前循环字段
 * </pre>
 * 这样用户在下拉列表里既能看到报表参数，也能看到循环上下文中的字段。
 *
 * <h3>与 JOIN 的关系</h3>
 * <p>循环是"关联两个表"的<b>第二种方式</b>（相对于 {@link com.codingapi.report.data.relation.Relationship} 的静态 JOIN）：
 * <ul>
 *   <li><b>静态 JOIN</b>：两个数据集在提取阶段一次性合并，适合两个表结构固定、关系明确</li>
 *   <li><b>循环驱动</b>：父迭代传键 → 子查询逐次执行，适合子源异构（甚至可以是 API），
 *       或者需要"模板重复呈现"的场景（如每人一张独立的薪资条）</li>
 * </ul>
 * 循环的代价是 N+1 次提取（工程上可通过批量预取优化）。
 */
@Data
@Builder
public class LoopBlock {
    /** 循环块唯一标识，被 {@link ValueRef.LoopField#loopBlockId()} 引用 */
    private String id;
    /** 显示标签，用于 UI 展示（如"员工薪资循环"） */
    private String label;
    /** 循环区域左上角坐标（模板中的起始格子） */
    private CellRef start;
    /** 循环区域右下角坐标（模板中的结束格子） */
    private CellRef end;
    /**
     * 驱动查询：数据集 + 过滤 + 分组，决定迭代集合。
     * <p>查询结果有多少行（或多少分组），循环就执行多少次。
     * 每次迭代，当前行的字段被发布到 ParamContext 的循环作用域中。
     */
    private Query source;
}
