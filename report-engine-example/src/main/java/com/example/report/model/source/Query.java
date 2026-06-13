package com.example.report.model.source;

import com.example.report.model.grid.Condition;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 查询定义：从<b>一个数据集</b>提取数据的意图。循环块的驱动源即用它，
 * 未来格子取数也可复用。
 *
 * <p>它正好压在"提取 / 加工"的边界上——本系统的数据计算全部在 Java，连接只负责提取：
 * <ul>
 *   <li>{@link #filters} 中"只引用本数据集列 + 常量/参数"的条件 →
 *       <b>可下推</b>到源 SELECT 的 WHERE，减少入内存的行数</li>
 *   <li>跨数据集 / join 之后的条件 → 留在 Java 加工层（不在 Query 里）</li>
 *   <li>{@link #groupBy} 非空 → 按分组<b>去重</b>迭代（如"按部门循环"）；
 *       为空 → 逐行迭代（如"按员工循环"，每人一张薪资条）</li>
 * </ul>
 */
@Data
@Builder
public class Query {
    /** 取数的数据集 */
    private String datasetId;
    /** 本数据集上的过滤条件；单源本地过滤可下推到提取阶段 */
    private List<Condition> filters;
    /** 分组字段（本数据集内字段名）；非空表示按分组去重迭代 */
    private List<String> groupBy;
    /** 排序字段 */
    private List<String> orderBy;
}
