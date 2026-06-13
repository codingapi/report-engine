package com.example.report.model;

import com.example.report.model.grid.CellBinding;
import com.example.report.model.grid.LoopBlock;
import com.example.report.model.param.Parameter;
import com.example.report.model.source.Relationship;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 报表（报表层）：引用一个 {@link DataModel}，只配置"<b>这张报表怎么摆</b>"。
 *
 * <p>同一个数据模型上可以建很多张报表，各自有不同的参数、格子布局、循环块，
 * 但共享底层的数据集与关系。
 *
 * <pre>
 *   Report
 *   ├── dataModelId        引用的可复用数据模型
 *   ├── templateId         模板画布：Univer/Excel 工作簿快照
 *   ├── extraRelationships 可选：报表私有关系（仅本报表需要，不污染共享模型）
 *   ├── parameters         报表级参数（External 即运行时输入契约）
 *   ├── cellBindings       覆盖在模板上的动态语义（文本/字段，见 CellBinding）
 *   └── loopBlocks         循环块（带迭代上下文的扩展容器，如薪资条）
 * </pre>
 *
 * <p><b>与 Univer/Excel 的关系：</b>静态文本、样式、边框、合并、行列尺寸全部在
 * {@code templateId} 指向的 Univer 工作簿里；本类的 {@code cellBindings} / {@code loopBlocks}
 * 只是覆盖层，最终序列化进模板的 {@code Cell.props} / {@code Sheet.loopBlocks}（已预留的槽位）。
 */
@Data
@Builder
public class Report {
    private String id;
    private String name;

    /** 引用的数据模型 id */
    private String dataModelId;

    /**
     * 模板画布 id：指向 Univer/Excel 工作簿快照（{@code report-engine-excel} 的 Workbook）。
     * 承载全部静态文本、样式、边框、合并、行列尺寸；cellBindings 按坐标覆盖其上。
     */
    private String templateId;

    /**
     * 报表私有关系：仅本报表需要、不值得放进共享模型的关联。
     * 作为 {@link DataModel#getRelationships()} 的补充，是例外而非常态。
     */
    private List<Relationship> extraRelationships;

    /** 报表级参数；External 参数合起来 = 这张报表的运行时输入契约 */
    private List<Parameter> parameters;

    private List<CellBinding> cellBindings;
    private List<LoopBlock> loopBlocks;
}
