package com.example.report.model.grid;

import com.example.report.model.source.FieldRef;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 数据字段格子：把一个数据集字段绑到格子，并声明它<b>如何扩展、跟谁对齐</b>。
 *
 * <p>报表的逻辑结构由这些属性涌现，不需要刚性的"表"对象——这正是类 Excel 报表的灵活之处。
 *
 * <p><b>多级分组统计表</b>（单位 → 部门 → 明细）就靠这几个属性串出来：每列纵向扩展，
 * 用 {@link #parentCell} 串成"父格链"逐级嵌套，分组列用 {@link #expandMode}=GROUP +
 * {@link #mergeRepeated}=true 把表头合并：
 * <pre>
 *   列     字段   expansion  expandMode  mergeRepeated  parentCell
 *   单位   单位   VERTICAL   GROUP       true           —
 *   部门   部门   VERTICAL   GROUP       true           单位格
 *   明细   数据   VERTICAL   LIST        false          部门格
 * </pre>
 *
 * <p>父格跨数据集嵌套时需配合 {@code Relationship}（JOIN 键）兜底。父格是"左父格(纵向)"
 * 还是"上父格(横向)"由父格自身的 {@link #expansion} 推断。
 */
@Data
@Builder
public final class FieldCell implements CellBinding {

    /** 绑定到哪个格子 */
    private CellRef cell;
    /** 绑定哪个数据集的哪个字段 */
    private FieldRef field;
    /** 行关系/列关系/不扩展 */
    private Expansion expansion;
    /** 扩展模式：分组去重 / 明细全行（仅 expansion != NONE 时有意义） */
    private ExpandMode expandMode;
    /** 相邻相同值是否合并成跨行/跨列单元格（多级分组表头常用） */
    private boolean mergeRepeated;
    /** 父格：对齐/嵌套的参照格，串成父格链实现多级分组；可为 null（顶层、独立扩展） */
    private CellRef parentCell;
    /** 聚合方式；expansion=NONE 时通常用它取单值 */
    private Aggregation aggregation;
    /** 该格的过滤条件，可引用参数或循环字段 */
    private List<Condition> conditions;
}
