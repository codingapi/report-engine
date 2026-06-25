package com.codingapi.report.core;

import com.codingapi.report.data.relation.Relationship;
import com.codingapi.report.param.Parameter;
import com.codingapi.report.core.grid.CellBinding;
import com.codingapi.report.core.grid.LoopBlock;
import com.codingapi.report.core.grid.SummaryRow;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 报表（报表层）：引用一个 {@link DataModel}，只配置"<b>这张报表怎么摆</b>"。
 *
 * <h3>DataModel 与 Report 的分工</h3>
 *
 * <p>同一个数据模型上可以建很多张报表，各自有不同的参数、格子布局、循环块， 但共享底层的数据集与关系。这是"数据定义"与"报表布局"的关注点分离：
 *
 * <pre>
 *   DataModel（数据定义，建一次复用）
 *   ├── datasources / datasets / relationships
 *   │
 *   Report A（怎么摆）──┐
 *   Report B（怎么摆）──┼─ 各自只关心布局和展示
 *   Report C（怎么摆）──┘
 * </pre>
 *
 * <h3>Report 的内部结构</h3>
 *
 * <pre>
 *   Report
 *   ├── dataModelId        引用的可复用数据模型（数据从哪来）
 *   ├── templateId         模板画布：Univer/Excel 工作簿快照（静态长什么样）
 *   ├── extraRelationships 可选：报表私有关系（仅本报表需要，不污染共享模型）
 *   ├── parameters         报表级参数（External 即运行时输入契约）
 *   ├── cellBindings       覆盖在模板上的动态语义（文本/字段绑定）
 *   ├── loopBlocks         循环块（带迭代上下文的扩展容器，如薪资条）
 *   └── summaries          小计/总计行定义
 * </pre>
 *
 * <h3>与 Univer/Excel 模板的关系</h3>
 *
 * <p>静态内容（纯标题文字、样式、边框、合并、行列尺寸）全部在 {@code templateId} 指向的 Univer 工作簿里，Report 不存储任何样式信息。{@code
 * cellBindings} / {@code loopBlocks} 只是语义覆盖层——告诉渲染引擎"这个格子要取什么数、怎么扩展"。 最终序列化时，cellBindings 写入 {@code
 * Cell.props}，loopBlocks 写入 {@code Sheet.loopBlocks}——这两个槽位 Univer 结构里已经预留。
 *
 * <h3>渲染流程中 Report 的角色</h3>
 *
 * <pre>
 *   ReportRenderer.render(dataModel, report, paramContext, templateWorkbook)
 *       1. 从 dataModel 取数据集定义和关系
 *       2. 按 report.cellBindings 驱动每个格子的取数和扩展
 *       3. 按 report.loopBlocks 驱动循环区域的迭代
 *       4. 按 report.summaries 插入小计/总计行
 *       5. 输出合并后的 Workbook
 * </pre>
 */
@Data
@Builder
public class Report {
    private String id;
    private String name;

    /**
     * 引用的数据模型 id，指向 {@link DataModel#getId()}。
     *
     * <p>一个 Report 只能引用一个 DataModel；一个 DataModel 可被多个 Report 引用。
     */
    private String dataModelId;

    /**
     * 模板画布 id：指向 Univer/Excel 工作簿快照（{@code report-engine-excel} 的 Workbook）。
     *
     * <p>承载全部静态文本、样式、边框、合并、行列尺寸。cellBindings 按坐标覆盖其上， 不影响模板本身的静态内容。
     */
    private String templateId;

    /**
     * 报表私有关系：仅本报表需要、不值得放进共享模型的关联。
     *
     * <p>作为 {@link DataModel#getRelationships()} 的补充，渲染时两者合并使用。 这是例外而非常态——大多数关系应该放在共享的 DataModel 里。
     *
     * <p>典型场景：某张报表需要关联一个临时导入的 CSV，这个关系其他报表都不需要。
     */
    private List<Relationship> extraRelationships;

    /**
     * 报表级参数列表。
     *
     * <p>其中 {@code ParamSource.External} 类型的参数合起来就是这张报表的运行时输入契约 （调用方 {@code render(reportId,
     * {deptId:5})} 时传入的键值对）。
     *
     * <p>循环块的迭代值不是参数，通过 {@link com.codingapi.report.expression.Value.LoopFieldValue} 直接引用。
     */
    private List<Parameter> parameters;

    /**
     * 格子绑定列表：覆盖在模板格子上的动态语义。
     *
     * <p>每个 {@link CellBinding} 包含值层（{@link com.codingapi.report.expression.Value} 表达式树）
     * 和控制层（扩展方向、分组模式、合并、父格链、过滤条件）。
     *
     * <ul>
     *   <li>值层 — 纯文本用 {@code Value.Template}，字段读取用 {@code Value.FieldValue}， 聚合用 {@code
     *       Value.Aggregate}，格式化用 {@code Value.FunctionCall}
     *   <li>控制层 — 由 expansion / expandMode / mergeRepeated / parentCell / conditions 控制 值如何在格子上铺开
     * </ul>
     *
     * 没有任何动态行为的纯静态格子（如表头"员工姓名"）不需要绑定，直接写在模板里。
     */
    private List<CellBinding> cellBindings;

    /**
     * 循环块列表：带迭代上下文的扩展容器。
     *
     * <p>典型场景：薪资条——每个人的薪资信息按模板循环呈现。 每个循环块绑定一个 {@link com.codingapi.report.data.dataset.Query}（数据集 +
     * 过滤 + 分组）， 决定迭代的范围和顺序。块内格子可通过 {@link com.codingapi.report.expression.Value.LoopFieldValue}
     * 引用当前迭代行的字段。
     */
    private List<LoopBlock> loopBlocks;

    /**
     * 小计/总计行定义：在分组断点处插入汇总行。
     *
     * <p>例如"按单位分组"的员工报表，可以在每个单位结束后插入一行"XX单位小计"， 全表末尾插入一行"总计"。行位置随数据量自适应。
     */
    private List<SummaryRow> summaries;
}
