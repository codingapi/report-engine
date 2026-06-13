package com.example.report.model.grid;

import com.example.report.model.source.FieldRef;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 数据字段格子：报表模型中<b>最核心的类</b>——把一个数据集字段绑到格子，
 * 并声明它如何扩展、跟谁对齐、是否聚合、是否过滤。
 *
 * <h3>为什么不需要刚性的"表"对象？</h3>
 * <p>传统报表引擎通常有 Table/Row/Column 这样的刚性结构，一张报表里能放几张表、
 * 几张表能嵌套都是预定义的。本系统用 FieldCell 的属性组合<b>涌现</b>出报表结构——
 * 每个格子独立声明"我展示什么数据、往哪个方向扩展、跟哪个格子对齐"，
 * 结构由这些局部声明自底向上组合出来。这正是类 Excel 报表的灵活之处：
 * 用户在 Excel 里想怎么摆就怎么摆，模型跟得上。
 *
 * <h3>核心属性解读</h3>
 *
 * <p><b>{@link #expansion}（扩展方向）</b>——数据在格子上的铺开方式：
 * <ul>
 *   <li>VERTICAL：一条记录占一行，纵向铺开 → 明细列表</li>
 *   <li>HORIZONTAL：一条记录占一列，横向铺开 → 动态月份列/指标列</li>
 *   <li>NONE：不扩展，通常配合 {@link #aggregation} 取单值（如"总人数"）</li>
 * </ul>
 *
 * <p><b>{@link #expandMode}（扩展模式）</b>——扩展时是否去重：
 * <ul>
 *   <li>GROUP：按值去重分组。用于多级分组表的分组列（单位、部门），
 *       常配合 {@link #mergeRepeated} 把相邻相同值合并成跨行/跨列单元格</li>
 *   <li>LIST：明细全行，每行都出，不去重。用于最内层的明细数据</li>
 * </ul>
 *
 * <p><b>{@link #parentCell}（父格）</b>——多级分组的灵魂。
 * 串成"父格链"实现逐级嵌套，是"左父格"还是"上父格"由父格自身的扩展方向推断：
 * <pre>
 *   多级分组统计表（单位 → 部门 → 明细）的属性组合：
 *
 *   列     字段   expansion  expandMode  mergeRepeated  parentCell
 *   ─────────────────────────────────────────────────────────────
 *   A 列   单位   VERTICAL   GROUP       true           null（顶层）
 *   B 列   部门   VERTICAL   GROUP       true           A 列（跟随单位）
 *   C 列   姓名   VERTICAL   LIST        false          B 列（跟随部门）
 *   D 列   工资   VERTICAL   LIST        false          B 列（跟随部门）
 *
 *   渲染结果：
 *   ┌──────┬──────┬──────┬──────┐
 *   │ 单位 │ 部门 │ 姓名 │ 工资 │
 *   ├──────┼──────┼──────┼──────┤
 *   │      │ 研发 │ 张三 │ 8000 │
 *   │ 总部 ├──────┼──────┼──────┤  ← mergeRepeated 把"总部"合并为跨行
 *   │      │ 研发 │ 李四 │ 9000 │
 *   │      ├──────┼──────┼──────┤
 *   │      │ 测试 │ 王五 │ 7500 │
 *   ├──────┼──────┼──────┼──────┤
 *   │ 分部 │ 销售 │ 赵六 │ 6000 │
 *   └──────┴──────┴──────┴──────┘
 * </pre>
 *
 * <h3>跨数据集的父格</h3>
 * <p>当父格和子格绑定的字段来自不同 Dataset 时（如父格是"部门名"来自 dept 表，
 * 子格是"员工名"来自 emp 表），渲染引擎通过 {@link com.example.report.model.source.Relationship}
 * 自动 JOIN 两个数据集，用 JOIN 键作为嵌套的关联条件。
 *
 * <h3>{@link #aggregation}（聚合）</h3>
 * <p>当 expansion=NONE 时，格子不扩展，需要把多条记录聚合成单个值
 * （如 COUNT(员工) → 总人数）。也可以配合扩展使用：在分组列旁边放一个
 * SUM(工资) 且 expansion=VERTICAL + GROUP，每个分组出一行汇总。
 *
 * @see com.example.report.model.source.FieldRef
 * @see Expansion
 * @see ExpandMode
 */
@Data
@Builder
public final class FieldCell implements CellBinding {

    /** 绑定到哪个格子（模板中的坐标） */
    private CellRef cell;

    /** 绑定哪个数据集的哪个字段。通过 datasetId + 字段名唯一定位 */
    private FieldRef field;

    /**
     * 扩展方向：数据在格子上的铺开方式。
     * <p>VERTICAL（一行一条记录）/ HORIZONTAL（一列一条记录）/ NONE（不扩展，取单值）。
     * VERTICAL + HORIZONTAL 正交组合可实现交叉表（矩阵报表）。
     */
    private Expansion expansion;

    /**
     * 扩展模式：扩展时是否去重。仅当 expansion != NONE 时有意义。
     * <p>GROUP（去重分组，用于分组列表头）/ LIST（明细全行，用于最内层数据）。
     */
    private ExpandMode expandMode;

    /**
     * 相邻相同值是否合并成跨行/跨列单元格。
     * <p>多级分组表头常用：单位列有 3 行"总部"，合并成一个跨 3 行的单元格。
     * 仅在 expandMode=GROUP 时有实际效果（LIST 模式下每行都不同，无需合并）。
     */
    private boolean mergeRepeated;

    /**
     * 父格：对齐/嵌套的参照格。
     * <p>串成父格链实现多级分组：子格跟随父格的分组节奏，父格每切换一次值，
     * 子格重新开始迭代。null 表示顶层格子，独立扩展不受约束。
     * <p>父格方向（左父格/上父格）由父格自身的 {@link #expansion} 推断：
     * VERTICAL 的父格 → 左父格（纵向对齐），HORIZONTAL 的父格 → 上父格（横向对齐）。
     */
    private CellRef parentCell;

    /**
     * 聚合方式。expansion=NONE 时通常用它取单值（如 COUNT → 总人数）。
     * <p>NONE 表示不聚合，直接取原值（明细模式下每行各自的值）。
     */
    private Aggregation aggregation;

    /**
     * 该格子上的过滤条件列表。
     * <p>条件右值可以是字面量、报表参数或循环字段（{@link com.example.report.model.param.ValueRef}），
     * 使过滤逻辑能动态响应运行时参数和循环上下文。
     * <p>与 {@link com.example.report.model.source.Query#getFilters()} 的区别：
     * Query 的过滤发生在数据提取阶段（全局），这里的过滤发生在渲染阶段（格子级别）。
     */
    private List<Condition> conditions;
}
