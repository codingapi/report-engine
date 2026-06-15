package com.codingapi.report.data.dataset;

import com.codingapi.report.operator.condition.Condition;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 查询定义：从<b>一个数据集</b>提取数据的意图。主要用作 {@link com.codingapi.report.render.grid.LoopBlock}
 * 的驱动源（决定循环迭代的范围和顺序）。
 *
 * <h3>在架构中的位置</h3>
 * <p>Query 正好压在"提取 / 加工"的边界上——本系统的数据计算全部在 Java 完成，
 * 连接只负责提取原始数据：
 * <pre>
 *   Query → DataExtractor.extract() → RawTable（提取阶段）
 *                                      ↓
 *                         Operators.filter/join/aggregate（加工阶段）
 * </pre>
 *
 * <h3>过滤条件的下推策略</h3>
 * <ul>
 *   <li>{@link #filters} 中"只引用本数据集列 + 常量/参数"的条件 →
 *       <b>可下推</b>到源 SELECT 的 WHERE，减少入内存的行数
 *       （如 DB 类型可直接拼成 SQL WHERE 子句）</li>
 *   <li>跨数据集 / join 之后的条件 → 留在 Java 加工层（不在 Query 里，
 *       而是放在 {@link com.codingapi.report.render.grid.CellBinding#getConditions()} 里）</li>
 * </ul>
 *
 * <h3>分组与迭代的关系</h3>
 * <ul>
 *   <li>{@link #groupBy} 非空 → 按分组<b>去重</b>迭代。
 *       例如"按部门循环"，迭代集合 = 去重后的部门列表，每个部门一次</li>
 *   <li>{@link #groupBy} 为空 → <b>逐行</b>迭代。
 *       例如"按员工循环"，每个员工一条记录，每人一张薪资条</li>
 * </ul>
 *
 * <h3>为什么不直接用 SQL 字符串？</h3>
 * <p>Query 是声明式的（描述"要什么"），不是过程式的（不写"怎么取"）。
 * 这样同一个 Query 可以适配不同 DataSourceType：DB 类型拼成 SQL，CSV 类型在内存过滤，
 * API 类型转为请求参数。
 */
@Data
@Builder
public class Query {
    /** 取数的数据集 id，指向 {@link Dataset#getId()} */
    private String datasetId;
    /**
     * 本数据集上的过滤条件。
     * <p>条件的左右值都是 {@link com.codingapi.report.expression.Value} 表达式，
     * 可以是字面量（{@code Value.Literal}）、报表参数（{@code Value.ParamValue}）、
     * 循环字段（{@code Value.LoopFieldValue}）等，使过滤范围能被运行时动态控制。
     */
    private List<com.codingapi.report.operator.condition.Condition> filters;
    /**
     * 分组字段列表（本数据集内的字段名）。
     * <p>非空时，循环按分组去重迭代（如按"部门"分组 → 每个部门一次）；
     * 为空时，循环逐行迭代（如每个员工一次）。
     */
    private List<String> groupBy;
    /**
     * 排序字段列表，控制提取结果的行顺序。
     * <p>排序影响报表输出的展示顺序（如按"入职日期"升序排列员工列表）。
     */
    private List<String> orderBy;
}
