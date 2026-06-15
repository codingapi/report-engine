package com.codingapi.report.render.grid;

import com.codingapi.report.expression.Value;
import com.codingapi.report.operator.condition.Condition;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 格子绑定（展现层覆盖）：附着在 Univer/Excel 模板某个格子上的<b>动态语义</b>。
 *
 * <h3>值层 + 控制层 分离</h3>
 * <p>一个 CellBinding 拆成两件互不干扰的事：
 * <ul>
 *   <li><b>值层</b>（{@link #value}）：这个格子最终显示<b>什么值</b>——纯文本、字段、聚合、格式化…
 *       全部统一为一棵 {@link Value} 表达式树，由 {@code ExpressionEngine} 求值。
 *       值层只关心"算出一个值"，不碰样式/边框/合并。</li>
 *   <li><b>控制层</b>（expansion / expandMode / mergeRepeated / parentCell / conditions）：
 *       这个值<b>怎么在格子上铺开</b>——纵向/横向扩展、分组去重、相邻合并、多级分组父格链、数据过滤。</li>
 * </ul>
 * 样式（字体/边框/富文本/数字格式）不在这里——那是模板层的事，渲染时从模板"声明格"继承。
 *
 * <h3>为什么不再用 TextCell/FieldCell 子类型？</h3>
 * <p>"文本格子 vs 字段格子"的区别其实只是<b>值的来源不同</b>，把它做成子类型等于用类型当值的开关。
 * 现在区别完全落在 {@link #value} 这棵表达式树的根节点上：纯文本 = {@code Template}/{@code Literal}，
 * 字段 = {@code FieldValue}，聚合 = {@code Aggregate}……控制层属性对所有格子一视同仁（按需配置）。
 *
 * <h3>渲染两层处理</h3>
 * <pre>
 *   1. 值层：ExpressionEngine.eval(value, 上下文) → 最终数据
 *   2. 控制层：按 expansion/merge 决定落在哪些格、占几行；样式从模板继承
 *   → 呈现到表格
 * </pre>
 */
@Data
@Builder
public class CellBinding {

    /** 绑定到哪个格子（模板中的坐标）。 */
    private CellRef cell;

    /**
     * 值层：该格子的值表达式树。
     * <p>纯文本 / 字段读取 / 聚合 / 格式化 等都统一表达为 {@link Value}，求值产出最终显示值。
     */
    private Value value;

    /**
     * 扩展方向：数据在格子上的铺开方式（VERTICAL 一行一记录 / HORIZONTAL 一列一记录 / NONE 不扩展）。
     * <p>为 null 视同 NONE（单值/文本格子）。
     */
    private Expansion expansion;

    /**
     * 扩展模式：扩展时是否去重（GROUP 分组去重 / LIST 明细全行）。仅 expansion != NONE 时有意义。
     */
    private ExpandMode expandMode;

    /** 相邻相同值是否合并成跨行/跨列单元格（多级分组表头常用，仅 GROUP 模式有效）。 */
    private boolean mergeRepeated;

    /**
     * 父格：多级分组的对齐参照格。串成父格链实现逐级嵌套；null 表示顶层。
     * 父格方向（左/上）由父格自身的 {@link #expansion} 推断。
     */
    private CellRef parentCell;

    /**
     * 该格子的数据过滤条件（格子级，渲染阶段生效）。
     * <p>条件右值可引用报表参数或循环字段，使过滤动态响应运行时上下文。
     */
    private List<Condition> conditions;
}
