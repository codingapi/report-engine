package com.codingapi.report.render.engine;

import com.codingapi.report.excel.pojo.Cell;
import com.codingapi.report.excel.pojo.Column;
import com.codingapi.report.excel.pojo.Merge;
import com.codingapi.report.excel.pojo.Row;
import com.codingapi.report.excel.pojo.Sheet;
import com.codingapi.report.excel.pojo.Workbook;
import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.render.Report;
import com.codingapi.report.render.grid.CellBinding;
import com.codingapi.report.render.grid.CellRef;
import com.codingapi.report.operator.condition.Condition;
import com.codingapi.report.render.grid.ExpandMode;
import com.codingapi.report.render.grid.Expansion;
import com.codingapi.report.render.grid.LoopBlock;
import com.codingapi.report.render.grid.SummaryCell;
import com.codingapi.report.render.grid.SummaryRow;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.dataset.UnionDataset;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.data.dataset.Query;
import com.codingapi.report.data.relation.Relationship;
import com.codingapi.report.data.dataset.UnionMember;
import com.codingapi.report.data.datasource.DataExtractor;
import com.codingapi.report.data.datasource.RawTable;
import com.codingapi.report.param.ParamContext;
import com.codingapi.report.expression.EvalContext;
import com.codingapi.report.expression.ExpressionEngine;
import com.codingapi.report.expression.Templates;
import com.codingapi.report.expression.Value;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 报表渲染器：引擎的核心类，把"报表配置 + 数据 + 运行时参数"算成一个 Univer/Excel {@link Workbook}。
 *
 * <h3>两种渲染模式</h3>
 * <ul>
 *   <li><b>无模板渲染</b>（{@link #render(DataModel, Report, ParamContext)}）：
 *       从零开始，只有数据和绑定，无样式。适合测试和数据验证。</li>
 *   <li><b>模板覆盖渲染</b>（{@link #render(DataModel, Report, ParamContext, Workbook)}）：
 *       以 Univer 画布为底，保留静态文本/样式/边框/合并/富文本，把动态值填进去。
 *       纵向扩展的列表行会继承"声明格"的样式（边框等随行复制）。
 *       这是生产模式——"模板层(视觉) + 覆盖层(数据)"分层的落地实现。</li>
 * </ul>
 *
 * <h3>渲染流程</h3>
 * <pre>
 *   render(dataModel, report, paramContext, templateWorkbook)
 *     1. seedTemplate — 把模板的格子（样式+值）和合并信息载入画布
 *     2. renderFree   — 渲染非循环区域的格子：
 *         a. 提取所有用到的数据集并按 Relationship join → 组合表
 *         b. 过滤（收集所有格子的条件 AND 求解）
 *         c. 纵向带渲染（分组列 + 明细列 + 聚合列，含小计/总计行插入）
 *         d. 文本格子（${placeholder} 替换）和单值格子（聚合/首值）
 *     3. renderLoop   — 渲染每个循环块：
 *         a. 提取驱动数据集并过滤/去重
 *         b. 逐次迭代：更新 ParamContext → 渲染块内格子（CellBinding 的 Value 表达式求值）
 *     4. buildWorkbook — 把画布转为 Workbook 输出
 * </pre>
 *
 * <h3>覆盖的结构类型</h3>
 * <ul>
 *   <li>简单列表（VERTICAL + LIST）：一行一条记录，纵向铺开</li>
 *   <li>带合并列表（GROUP + mergeRepeated）：分组列相邻相同值合并为跨行单元格</li>
 *   <li>统计列表（GROUP + 聚合列）：分组 + SUM/COUNT 等聚合</li>
 *   <li>循环块（LoopBlock）：模板区域重复呈现，每次迭代独立取数</li>
 * </ul>
 * <p>交叉表（HORIZONTAL 扩展）尚未实现。
 *
 * <h3>内部画布模型</h3>
 * <p>{@link Canvas} 是一个轻量中间结构：{@code Map<row:col, Cell>} + merges 列表。
 * 渲染过程就是往画布里放格子（{@link #place}），最后一次性转为 Workbook。
 * 这样避免了直接操作 Workbook 的复杂性（坐标偏移、行插入等）。
 */
public class ReportRenderer {

    private static final JsonNodeFactory NF = JsonNodeFactory.instance;

    /** 注册的提取器列表，每种 DataSourceType 一个实现 */
    private final List<DataExtractor> extractors;

    /** 当前渲染使用的数据模型（render 时设置，render 结束后不再使用） */
    private DataModel dm;
    /** 数据集提取缓存：同一数据集在一次渲染中只提取一次，后续直接复用 */
    private final Map<String, RawTable> cache = new HashMap<>();
    /** 表达式引擎：所有格子的值（文本插值/字段/聚合/格式化）统一经它求值 */
    private final ExpressionEngine engine = new ExpressionEngine();

    /**
     * @param extractors 已注册的提取器列表（如 [CsvDataExtractor, DbDataExtractor, ...]）
     */
    public ReportRenderer(List<DataExtractor> extractors) {
        this.extractors = extractors;
    }

    /**
     * 无模板渲染：从零开始，只有数据和绑定，无样式。
     * <p>适合测试和数据验证场景。
     */
    public Workbook render(DataModel dm, Report report, ParamContext ctx) {
        return render(dm, report, ctx, null);
    }

    /**
     * 模板覆盖渲染：以 Univer 画布为底，保留其样式/边框/合并/富文本，动态值覆盖其上。
     *
     * @param dm       数据模型（数据集 + 关系定义）
     * @param report   报表定义（格子绑定 + 循环块 + 小计/总计）
     * @param ctx      运行时参数上下文
     * @param template Univer 模板画布（null 时退化为无模板渲染）
     * @return 渲染后的 Workbook，可直接传给 ExcelExporter 生成 .xlsx
     */
    public Workbook render(DataModel dm, Report report, ParamContext ctx, Workbook template) {
        this.dm = dm;
        this.cache.clear();

        Canvas canvas = new Canvas();
        seedTemplate(canvas, template);

        renderFree(report, ctx, canvas);
        if (report.getLoopBlocks() != null) {
            for (LoopBlock loop : report.getLoopBlocks()) {
                renderLoop(report, loop, ctx, canvas);
            }
        }
        return buildWorkbook(canvas);
    }

    // ============================================================
    // 非循环格子：文本 + 纵向带（分组/列表/聚合列）+ 单值聚合
    // ============================================================

    /**
     * 渲染非循环区域的所有格子。
     *
     * <h3>处理步骤</h3>
     * <ol>
     *   <li>分类：收集 cellBindings（跳过属于循环块范围内的格子），
     *       按 expansion 分为纵向带和单值格</li>
     *   <li>按数据集连通性分组：用 union-find 按 Relationship 图将数据格分为连通分量</li>
     *   <li>每组独立渲染纵向带：组内 JOIN + 过滤 + renderBand，各自有独立的行数（shift）</li>
     *   <li>单值格用 max(shifts) 做行偏移，保证位于带下方的总计行/页脚正确下移</li>
     * </ol>
     *
     * <h3>独立数据带</h3>
     * <p>当报表中引用了多个无关系的数据集时（如员工表 + 商品表并排），它们属于不同的
     * 连通分量，各自独立展开——行数可以不同，互不影响。这是"并列独立数据区"报表模式的核心支持。
     */
    private void renderFree(Report report, ParamContext ctx, Canvas canvas) {
        // 1. 收集非循环区格子
        List<CellBinding> band = new ArrayList<>();
        List<CellBinding> singles = new ArrayList<>();
        List<CellBinding> dataCells = new ArrayList<>();
        // 独立纵向带：显式标记的列，从自己的声明行独立展开，不与同源列对齐
        List<CellBinding> independentCells = new ArrayList<>();
        for (CellBinding b : report.getCellBindings()) {
            if (inAnyLoop(report, b.getCell())) {
                continue;
            }
            (b.getExpansion() == Expansion.VERTICAL ? band : singles).add(b);
            if (b.isIndependent() && b.getExpansion() == Expansion.VERTICAL && referencesData(b)) {
                independentCells.add(b);
            } else if (referencesData(b)) {
                dataCells.add(b);
            }
        }

        // 2. 按数据集连通性分组（仅对齐带；独立带单独处理）
        List<List<CellBinding>> groups = groupByConnectivity(dataCells);

        // 3. 每组独立渲染纵向带
        int globalShift = 0;
        int globalBandBase = Integer.MAX_VALUE;
        RawTable firstGroupFiltered = null;
        // 记录每条带的基准行和"净插入行数"，用于后续下移模板 merge / 静态格
        List<int[]> bandRecords = new ArrayList<>();
        // 模板原始 merge 数量（renderBand 会追加新 merge，只对模板 merge 做下移）
        int templateMergeCount = canvas.merges.size();
        // 汇总行位置：模板行号 → 输出行号（用于精确下移跟随汇总的模板 merge）
        Map<Integer, Integer> summaryOutputRows = new HashMap<>();
        // 所有带占据的设计行下沿（带声明行 + 汇总声明行的最大值），其下方的静态格才需要下移
        int footprintBottom = Integer.MIN_VALUE;
        // 所有汇总声明行的集合：这些行由汇总自身渲染，静态格不在此处搬运
        Set<Integer> summaryDesignRows = new HashSet<>();

        for (List<CellBinding> group : groups) {
            // 组内 band cells
            List<CellBinding> groupBand = new ArrayList<>();
            for (CellBinding b : group) {
                if (b.getExpansion() == Expansion.VERTICAL) {
                    groupBand.add(b);
                }
            }
            if (groupBand.isEmpty()) continue;

            // 组内 JOIN + 过滤
            RawTable combined = buildCombinedTable(group);
            RawTable filtered = Operators.filter(combined, collectConditions(group), ctx, engine);

            if (firstGroupFiltered == null) {
                firstGroupFiltered = filtered;
            }

            // 组内 bandBase
            int groupBandBase = Integer.MAX_VALUE;
            for (CellBinding b : groupBand) {
                groupBandBase = Math.min(groupBandBase, b.getCell().row());
            }
            globalBandBase = Math.min(globalBandBase, groupBandBase);

            // 渲染带：只把"列落在本带"的汇总行交给本带，避免并列报表互相串扰
            List<SummaryRow> groupSummaries = summariesForBand(report.getSummaries(), groupBand);
            // 统计本带占用的"汇总声明行"：每个声明行只算一行设计行，不计入插入量
            int maxDesignRow = groupBandBase;
            int designSummaryRows = 0;
            Set<Integer> seenSdRows = new HashSet<>();
            for (SummaryRow s : groupSummaries) {
                if (s.getRow() != null && seenSdRows.add(s.getRow())) {
                    designSummaryRows++;
                    maxDesignRow = Math.max(maxDesignRow, s.getRow());
                }
            }
            summaryDesignRows.addAll(seenSdRows);
            footprintBottom = Math.max(footprintBottom, maxDesignRow);

            int n = renderBand(groupBand, groupSummaries, filtered, groupBandBase, ctx, canvas, summaryOutputRows);
            // 净插入行数 = 输出行数 - 已占用的设计行数（带声明行 1 行 + 各汇总声明行）。
            // 汇总声明行本身已在设计中占位，不应被重复计入扩展量，否则带下方内容会多移。
            int insertion = Math.max(0, n - (1 + designSummaryRows));
            globalShift = Math.max(globalShift, insertion);
            bandRecords.add(new int[]{groupBandBase, insertion});
        }

        // 3a-独立带：显式标记的独立列，按声明行分组，各自从自己的声明行起独立向下展开。
        //   它们是"绝对锚定"的覆盖层：固定在声明行，不随对齐带扩展而下移，也不参与下方内容的位移、
        //   汇总与模板 merge 下移（故不加入 bandRecords / globalShift / footprint）。
        for (List<CellBinding> indepGroup : groupIndependentByRow(independentCells)) {
            RawTable combined = buildCombinedTable(indepGroup);
            RawTable filtered = Operators.filter(combined, collectConditions(indepGroup), ctx, engine);
            int indepBase = Integer.MAX_VALUE;
            for (CellBinding b : indepGroup) {
                indepBase = Math.min(indepBase, b.getCell().row());
            }
            renderBand(indepGroup, List.of(), filtered, indepBase, ctx, canvas);
        }

        // 3b. 下移模板 merge：纵向带扩展后，带下方和汇总行的模板合并区域需要跟随下移
        //     仅处理 seedTemplate 载入的原始 merge（renderBand/mergeColumn 动态创建的 merge 已在正确位置）
        for (int mi = 0; mi < templateMergeCount; mi++) {
            Merge m = canvas.merges.get(mi);
            Integer summaryOutRow = summaryOutputRows.get(m.getStartRow());

            if (summaryOutRow != null) {
                // 汇总行的 merge：直接移至汇总行的输出位置
                m.setStartRow(summaryOutRow);
            } else if (m.getStartRow() >= globalBandBase + 1) {
                // 带下方的非汇总 merge：按累计 shift 下移
                int mergeShift = 0;
                for (int[] rec : bandRecords) {
                    if (m.getStartRow() >= rec[0] + 1) {
                        mergeShift += rec[1];
                    }
                }
                if (mergeShift > 0) {
                    m.setStartRow(m.getStartRow() + mergeShift);
                }
            }
            // 其他（带扩展范围内的非汇总 merge）：属于模板残留，随数据覆盖，不下移
        }

        // 3c. 下移带下方的静态模板格子：标题/页脚等无绑定的模板内容（如末行说明文字）
        //     必须随数据扩展一起下移，否则会被展开的数据行覆盖或停留在错误位置。
        //     只搬运"位于带下沿之下、且非汇总声明行、且非绑定格"的纯静态格。
        if (footprintBottom != Integer.MIN_VALUE && !canvas.template.isEmpty()) {
            Set<String> bindingKeys = new HashSet<>();
            for (CellBinding b : report.getCellBindings()) {
                if (!inAnyLoop(report, b.getCell())) {
                    bindingKeys.add(key(b.getCell().row(), b.getCell().column()));
                }
            }
            // 先快照需要搬运的模板格（避免遍历时修改 canvas.cells）
            List<Cell> staticCells = new ArrayList<>(canvas.template.values());
            for (Cell tc : staticCells) {
                int r = tc.getRow();
                if (r <= footprintBottom || summaryDesignRows.contains(r)
                        || bindingKeys.contains(key(r, tc.getCol()))) {
                    continue;
                }
                int sh = 0;
                for (int[] rec : bandRecords) {
                    if (r >= rec[0] + 1) {
                        sh += rec[1];
                    }
                }
                if (sh == 0) {
                    continue; // 带未扩展，静态格原地不动（也未被数据覆盖）
                }
                String origKey = key(r, tc.getCol());
                // 原位若未被数据动态写入，则是纯静态残留，需移除避免重影
                if (!canvas.dynamic.contains(origKey)) {
                    canvas.cells.remove(origKey);
                }
                Cell moved = new Cell();
                moved.setRow(r + sh);
                moved.setCol(tc.getCol());
                moved.setValue(tc.getValue());
                moved.setRichText(tc.getRichText());
                moved.setStyle(tc.getStyle());
                canvas.cells.put(key(r + sh, tc.getCol()), moved);
            }
        }

        // 3d. 行高位移：模板自定义行高也要随带扩展跟随到输出行，否则页脚/汇总行的行高会错位
        //     （带声明行的行高保留在首条数据行；汇总声明行跟随到汇总输出行；带下方行按累计 shift 下移）
        if (footprintBottom != Integer.MIN_VALUE && !canvas.templateRows.isEmpty()) {
            for (Row rw : canvas.templateRows) {
                int r = rw.getIndex();
                Integer summaryOutRow = summaryOutputRows.get(r);
                if (summaryOutRow != null) {
                    rw.setIndex(summaryOutRow);
                } else if (r >= globalBandBase + 1) {
                    int sh = 0;
                    for (int[] rec : bandRecords) {
                        if (r >= rec[0] + 1) {
                            sh += rec[1];
                        }
                    }
                    rw.setIndex(r + sh);
                }
            }
        }

        // 3e. 清理带声明格的残留占位文本：带统一从 bandBase 起逐行填充，
        //     若某绑定的声明格本身不落在输出范围内（如声明在 B12，但带从第 0 行展开，
        //     数据行数不足以覆盖到第 12 行），其设计期占位文本不会被数据覆盖 →
        //     必须清除，否则导出会残留一个孤立的字段占位（如 B12 处多出的"性别"）。
        for (CellBinding b : band) {
            String k = key(b.getCell().row(), b.getCell().column());
            if (!canvas.dynamic.contains(k)) {
                canvas.cells.remove(k);
            }
        }

        final int shift = globalShift;
        final int base = globalBandBase;
        final RawTable evalTable = firstGroupFiltered;

        // 4. 单值/文本格子：表达式求值，带下方的行下移 shift
        for (CellBinding b : singles) {
            Object v = evalSingle(b, evalTable, ctx);
            int row = b.getCell().row() > base ? b.getCell().row() + shift : b.getCell().row();
            place(canvas, b.getCell(), row, b.getCell().column(), v);
        }
    }

    /**
     * 把汇总行按「列区间归属」过滤到某个数据带。
     * <p>并列独立报表（多个无关系数据集排在同一批行上）共享同一份 {@link SummaryRow} 列表，
     * 但每行汇总只应作用于它显式声明的列区间 [fromColumn, toColumn] 所覆盖的那个带。若不过滤，
     * 一个带的小计/总计会被广播到其它带——尤其是分组小计在别的带里匹配不到分组列时会退化成
     * 总计，污染并列报表。
     * <p>归属判定：汇总行的列区间与本带占据的列集合有交集，即归属本带。
     */
    private static List<SummaryRow> summariesForBand(List<SummaryRow> summaries, List<CellBinding> groupBand) {
        if (summaries == null || summaries.isEmpty()) {
            return List.of();
        }
        Set<Integer> bandCols = new HashSet<>();
        for (CellBinding b : groupBand) {
            bandCols.add(b.getCell().column());
        }
        List<SummaryRow> result = new ArrayList<>();
        for (SummaryRow s : summaries) {
            for (int col = s.getFromColumn(); col <= s.getToColumn(); col++) {
                if (bandCols.contains(col)) {
                    result.add(s);
                    break;
                }
            }
        }
        return result;
    }

    // ============================================================
    // 数据集连通性分组（union-find）
    // ============================================================

    /**
     * 按数据集连通性将 CellBinding 分组。
     * <p>两个数据集如果在 DataModel 的 Relationship 图中连通（直接或间接），
     * 则归入同一组，渲染时走 JOIN 同行迭代；不连通的各自独立渲染。
     */
    /**
     * 独立带按声明行分组：同一声明行的独立列归一组（彼此对齐成一条小带），不同声明行各自成组。
     * <p>这样既支持"单列从某行独立展开"，也支持"同一行的若干独立列对齐成一段并列子表"。
     */
    private List<List<CellBinding>> groupIndependentByRow(List<CellBinding> cells) {
        if (cells.isEmpty()) return List.of();
        Map<Integer, List<CellBinding>> byRow = new LinkedHashMap<>();
        for (CellBinding b : cells) {
            byRow.computeIfAbsent(b.getCell().row(), k -> new ArrayList<>()).add(b);
        }
        return new ArrayList<>(byRow.values());
    }

    private List<List<CellBinding>> groupByConnectivity(List<CellBinding> dataCells) {
        if (dataCells.isEmpty()) return List.of();

        // 收集所有引用的 datasetId
        Set<String> allIds = new LinkedHashSet<>();
        for (CellBinding b : dataCells) {
            List<FieldRef> refs = new ArrayList<>();
            collectFieldRefs(b.getValue(), refs);
            if (b.getConditions() != null) {
                for (Condition c : b.getConditions()) {
                    collectFieldRefs(c.getLeft(), refs);
                    collectFieldRefs(c.getRight(), refs);
                }
            }
            for (FieldRef r : refs) {
                allIds.add(r.datasetId());
            }
        }

        List<String> ids = new ArrayList<>(allIds);
        if (ids.size() <= 1) {
            // 只有一个数据集（或无），不需要分组
            return List.of(dataCells);
        }

        // Union-find 初始化
        Map<String, String> parent = new HashMap<>();
        for (String id : ids) {
            parent.put(id, id);
        }

        // 按 Relationship 合并
        if (dm.getRelationships() != null) {
            for (Relationship rel : dm.getRelationships()) {
                String a = rel.getLeft().datasetId();
                String b = rel.getRight().datasetId();
                if (allIds.contains(a) && allIds.contains(b)) {
                    union(parent, a, b);
                }
            }
        }

        // 按根节点分组 datasetId
        Map<String, List<String>> idGroups = new LinkedHashMap<>();
        for (String id : ids) {
            idGroups.computeIfAbsent(find(parent, id), k -> new ArrayList<>()).add(id);
        }

        // 只有一个连通分量 → 全部归一组
        if (idGroups.size() == 1) {
            return List.of(dataCells);
        }

        // 将 CellBinding 分配到对应组
        Map<String, List<CellBinding>> cellGroups = new LinkedHashMap<>();
        for (String root : idGroups.keySet()) {
            cellGroups.put(root, new ArrayList<>());
        }
        for (CellBinding b : dataCells) {
            List<FieldRef> refs = new ArrayList<>();
            collectFieldRefs(b.getValue(), refs);
            if (b.getConditions() != null) {
                for (Condition c : b.getConditions()) {
                    collectFieldRefs(c.getLeft(), refs);
                    collectFieldRefs(c.getRight(), refs);
                }
            }
            String dsId = refs.isEmpty() ? null : refs.get(0).datasetId();
            String root = dsId != null ? find(parent, dsId) : idGroups.keySet().iterator().next();
            cellGroups.get(root).add(b);
        }

        return new ArrayList<>(cellGroups.values());
    }

    private String find(Map<String, String> parent, String x) {
        while (!parent.get(x).equals(x)) {
            parent.put(x, parent.get(parent.get(x))); // 路径压缩
            x = parent.get(x);
        }
        return x;
    }

    private void union(Map<String, String> parent, String a, String b) {
        String ra = find(parent, a);
        String rb = find(parent, b);
        if (!ra.equals(rb)) {
            parent.put(ra, rb);
        }
    }

    /** 单值/文本格子求值：含聚合走行集合，其余走首行（无数据则空行，纯文本/参数照样可算）。 */
    private Object evalSingle(CellBinding b, RawTable filtered, ParamContext ctx) {
        if (Templates.containsAggregate(b.getValue())) {
            List<Map<String, Object>> rows = filtered == null ? List.of() : filtered.getRows();
            return engine.eval(b.getValue(), EvalContext.aggregate(rows, ctx));
        }
        Map<String, Object> row = (filtered != null && !filtered.getRows().isEmpty())
                ? filtered.getRows().get(0) : null;
        return engine.eval(b.getValue(), EvalContext.scalar(row, ctx));
    }

    /**
     * 渲染一条纵向带，游标 + 控制断点：
     * 明细行按分组排序输出；分组断点处插入对应层级的小计行；末尾插入总计行。
     * 行位置由游标决定（小计/总计会把后续行下推）。返回产出的总行数。
     *
     * <p>聚合列（agg）按 parentCell 决定汇总层级：parent 指向粗粒度分组列 → 跨该组明细行合并
     * （如"总人数"按单位汇总、跨部门行合并）；不指定则按最细粒度逐行算。
     */
    private int renderBand(List<CellBinding> band, List<SummaryRow> summaries, RawTable filtered,
                           int bandBase, ParamContext ctx, Canvas canvas) {
        return renderBand(band, summaries, filtered, bandBase, ctx, canvas, null);
    }

    /**
     * 同上，额外收集汇总行的位置到 {@code summaryOutputRows}（非 null 时启用）。
     * <p>key = 汇总行的模板行号（{@code SummaryRow.row}），value = 该汇总行的输出行号。
     * 用于 renderFree 的模板 merge 下移：汇总行处的 merge 应跟随汇总行移动。
     */
    private int renderBand(List<CellBinding> band, List<SummaryRow> summaries, RawTable filtered,
                           int bandBase, ParamContext ctx, Canvas canvas,
                           Map<Integer, Integer> summaryOutputRows) {
        List<CellBinding> groupCols = new ArrayList<>();
        List<CellBinding> listCols = new ArrayList<>();
        List<CellBinding> aggCols = new ArrayList<>();
        for (CellBinding b : band) {
            if (isAgg(b)) {
                aggCols.add(b);
            } else if (b.getExpandMode() == ExpandMode.GROUP) {
                groupCols.add(b);
            } else {
                listCols.add(b);
            }
        }

        Map<CellRef, CellBinding> groupByCell = new HashMap<>();
        for (CellBinding gc : groupCols) {
            groupByCell.put(gc.getCell(), gc);
        }
        groupCols.sort(Comparator.comparingInt(gc -> depth(gc, groupByCell)));

        List<Map<String, Object>> rows = new ArrayList<>(filtered.getRows());
        rows.sort(Comparator.comparing(r -> tupleKey(r, groupCols)));

        boolean hasDetail = !listCols.isEmpty();
        List<Unit> details = hasDetail ? oneUnitPerRow(rows, groupCols) : groupByTuple(rows, groupCols);

        // 小计/总计按层级归类（level = 分组列下标；-1 = 总计）
        Map<Integer, List<SummaryRow>> byLevel = new HashMap<>();
        if (summaries != null) {
            for (SummaryRow s : summaries) {
                int level = -1;
                if (s.getGroupBy() != null) {
                    for (int d = 0; d < groupCols.size(); d++) {
                        if (s.getGroupBy().equals(fieldOf(groupCols.get(d)))) {
                            level = d;
                            break;
                        }
                    }
                }
                byLevel.computeIfAbsent(level, k -> new ArrayList<>()).add(s);
            }
        }

        // 控制断点：明细行 + 断点处插小计
        List<Out> seq = new ArrayList<>();
        int[] groupStart = new int[Math.max(1, groupCols.size())];
        for (int i = 0; i < details.size(); i++) {
            seq.add(detailOut(details.get(i), groupCols, listCols, aggCols, rows, groupByCell, ctx));
            boolean last = i == details.size() - 1;
            int breakLevel = last ? 0 : firstDiffLevel(details.get(i).tuple, details.get(i + 1).tuple, groupCols.size());
            if (last || breakLevel >= 0) {
                int to = last ? 0 : breakLevel;
                for (int d = groupCols.size() - 1; d >= to; d--) {
                    List<SummaryRow> ss = byLevel.get(d);
                    if (ss != null) {
                        List<Map<String, Object>> groupRows = flatten(details, groupStart[d], i);
                        Object groupVal = details.get(i).tuple.get(d);
                        for (SummaryRow s : ss) {
                            seq.add(summaryOut(s, groupRows, groupVal, ctx));
                            if (summaryOutputRows != null && s.getRow() != null) {
                                summaryOutputRows.put(s.getRow(), bandBase + seq.size() - 1);
                            }
                        }
                    }
                }
                for (int d = to; d < groupCols.size(); d++) {
                    groupStart[d] = i + 1;
                }
            }
        }
        List<SummaryRow> grand = byLevel.get(-1);
        if (grand != null) {
            for (SummaryRow s : grand) {
                seq.add(summaryOut(s, rows, null, ctx));
                if (summaryOutputRows != null && s.getRow() != null) {
                    summaryOutputRows.put(s.getRow(), bandBase + seq.size() - 1);
                }
            }
        }

        // 落格（游标 = bandBase + 序号）
        for (int k = 0; k < seq.size(); k++) {
            Out o = seq.get(k);
            int outRow = bandBase + k;
            for (Map.Entry<Integer, Object> e : o.values.entrySet()) {
                place(canvas, o.sources.get(e.getKey()), outRow, e.getKey(), e.getValue());
            }
        }

        // 分组列合并：相同前缀(0..d)的连续明细行
        for (int d = 0; d < groupCols.size(); d++) {
            CellBinding gc = groupCols.get(d);
            if (gc.isMergeRepeated()) {
                mergeColumn(canvas, seq, bandBase, gc.getCell().column(), d + 1);
            }
        }
        // 粗粒度聚合列合并（如总人数按单位跨行合并）
        for (CellBinding ac : aggCols) {
            int p = aggPrefixLen(ac, groupCols, groupByCell);
            if (ac.isMergeRepeated() && p < groupCols.size()) {
                mergeColumn(canvas, seq, bandBase, ac.getCell().column(), p);
            }
        }
        return seq.size();
    }

    private Out detailOut(Unit unit, List<CellBinding> groupCols, List<CellBinding> listCols,
                          List<CellBinding> aggCols, List<Map<String, Object>> rows,
                          Map<CellRef, CellBinding> groupByCell, ParamContext ctx) {
        Out o = new Out(true, unit.tuple);
        for (int d = 0; d < groupCols.size(); d++) {
            CellBinding gc = groupCols.get(d);
            o.values.put(gc.getCell().column(), unit.tuple.get(d));
            o.sources.put(gc.getCell().column(), gc.getCell());
        }
        for (CellBinding lc : listCols) {
            o.values.put(lc.getCell().column(), engine.eval(lc.getValue(), EvalContext.scalar(unit.rows.get(0), ctx)));
            o.sources.put(lc.getCell().column(), lc.getCell());
        }
        for (CellBinding ac : aggCols) {
            int p = aggPrefixLen(ac, groupCols, groupByCell);
            List<Map<String, Object>> groupRows = rowsWithPrefix(rows, groupCols, unit.tuple, p);
            o.values.put(ac.getCell().column(), engine.eval(ac.getValue(), EvalContext.aggregate(groupRows, ctx)));
            o.sources.put(ac.getCell().column(), ac.getCell());
        }
        return o;
    }

    private Out summaryOut(SummaryRow s, List<Map<String, Object>> groupRows, Object groupVal, ParamContext ctx) {
        Out o = new Out(false, null);
        // 注入 group = 当前分组值，供标签里的 ${group} 解析
        EvalContext ec = EvalContext.aggregate(groupRows, ctx).withLocal("group", groupVal);
        for (SummaryCell sc : s.getCells()) {
            o.values.put(sc.getColumn(), engine.eval(sc.getValue(), ec));
            // 汇总行从模板的"汇总声明行"继承样式（边框/字体随汇总行滚动到输出位置）
            if (s.getRow() != null) {
                o.sources.put(sc.getColumn(), new CellRef(null, s.getRow(), sc.getColumn()));
            }
        }
        // 补齐列区间 [fromColumn, toColumn] 内"无汇总格"的空列：从模板汇总声明行继承样式。
        // 否则未定义汇总格的列在汇总行输出位置完全无格 → 边框等样式缺失（小计行尤其明显：
        // 它有多个输出实例，靠 renderFree 搬运单条模板声明行的静态格只能补到其中一行）。
        // 仅在模板态（s.row 非 null）补齐，无模板的测试/数据校验场景保持原样不变。
        if (s.getRow() != null) {
            for (int col = s.getFromColumn(); col <= s.getToColumn(); col++) {
                if (!o.values.containsKey(col)) {
                    o.values.put(col, null);
                    o.sources.put(col, new CellRef(null, s.getRow(), col));
                }
            }
        }
        return o;
    }

    // ============================================================
    // 循环块：逐迭代重复整个块
    // ============================================================

    /**
     * 渲染一个循环块：驱动查询取数 → 逐次迭代 → 块内格子按当前迭代行取数。
     *
     * <h3>处理步骤</h3>
     * <ol>
     *   <li>提取驱动数据集并按 Query 的 filters 过滤</li>
     *   <li>如果 Query 有 groupBy，对驱动数据做去重（按分组迭代而非逐行迭代）</li>
     *   <li>计算块高度（end.row - start.row + 1），每次迭代的行偏移 = i × height</li>
     *   <li>收集块范围内的所有 cellBindings</li>
     *   <li>逐次迭代：
     *       <ul>
     *         <li>更新 ParamContext 的循环作用域（当前行的字段值）</li>
     *         <li>CellBinding 的 Value 表达式求值（循环字段优先于报表参数）</li>
     *         <li>绑定驱动数据集的字段：直接从当前迭代行取值</li>
     *         <li>绑定其他数据集的字段：独立提取 + 按条件过滤（条件里可引用循环字段）+ 取首值或聚合</li>
     *       </ul></li>
     * </ol>
     *
     * <h3>跨数据集取数（子查询模式）</h3>
     * <p>当循环块内的 CellBinding 绑定的是非驱动数据集时（如循环员工，但格子显示的是该员工的学历），
     * 引擎独立提取该数据集并按格子的 conditions 过滤。条件右值可以引用循环字段
     * （{@link com.codingapi.report.expression.Value.LoopFieldValue}），
     * 实现"父迭代传键 → 子查询"的效果。
     */
    private void renderLoop(Report report, LoopBlock loop, ParamContext ctx, Canvas canvas) {
        Query q = loop.getSource();
        // 1. 提取驱动数据集 + 过滤
        RawTable driving = Operators.filter(extract(q.getDatasetId()), q.getFilters(), ctx, engine);
        // 2. groupBy 去重（按分组迭代而非逐行）
        if (q.getGroupBy() != null && !q.getGroupBy().isEmpty()) {
            driving = distinctBy(driving, q.getDatasetId(), q.getGroupBy());
        }

        // 3. 块高度：模板中循环区域占几行，每次迭代重复这么多行
        int height = loop.getEnd().row() - loop.getStart().row() + 1;

        // 4. 收集块范围内的所有格子绑定
        List<CellBinding> blockCells = new ArrayList<>();
        for (CellBinding b : report.getCellBindings()) {
            if (inLoop(loop, b.getCell())) {
                blockCells.add(b);
            }
        }

        // 4b. 收集块范围内的模板合并区域（用于后续迭代时复制）
        int loopStartRow = loop.getStart().row();
        int loopEndRow = loop.getEnd().row();
        int loopStartCol = loop.getStart().column();
        int loopEndCol = loop.getEnd().column();
        List<Merge> blockMerges = new ArrayList<>();
        for (Merge m : canvas.merges) {
            if (m.getStartRow() >= loopStartRow && m.getStartRow() + m.getRowSpan() - 1 <= loopEndRow) {
                blockMerges.add(m);
            }
        }

        // 4c. 收集块内"无绑定的静态模板格"（表头文字 / 纯边框空格等）。
        //     后续迭代只 place 绑定格 + 复制 merge，这些静态格不会自动重复 → 须按 rowOffset 复制，
        //     否则第二条起的薪资条表头/边框残缺。
        Set<String> blockBindingKeys = new HashSet<>();
        for (CellBinding b : blockCells) {
            blockBindingKeys.add(key(b.getCell().row(), b.getCell().column()));
        }
        List<Cell> blockStaticCells = new ArrayList<>();
        for (Cell tc : canvas.template.values()) {
            if (tc.getRow() >= loopStartRow && tc.getRow() <= loopEndRow
                    && tc.getCol() >= loopStartCol && tc.getCol() <= loopEndCol
                    && !blockBindingKeys.contains(key(tc.getRow(), tc.getCol()))) {
                blockStaticCells.add(tc);
            }
        }

        // 5. 逐次迭代
        for (int i = 0; i < driving.getRows().size(); i++) {
            Map<String, Object> drow = driving.getRows().get(i);
            // 更新循环作用域：去掉限定名前缀（循环内字段名天然唯一）
            ctx.setLoopRow(loop.getId(), unqualify(drow, q.getDatasetId()));
            int rowOffset = i * height;  // 每次迭代的行偏移

            for (CellBinding b : blockCells) {
                int row = b.getCell().row() + rowOffset;
                int col = b.getCell().column();
                place(canvas, b.getCell(), row, col, evalLoopCell(b, q.getDatasetId(), drow, ctx));
            }

            // 为后续迭代复制合并区域 + 静态模板格（第一次迭代的内容已由 seedTemplate 载入）
            if (i > 0) {
                for (Merge orig : blockMerges) {
                    Merge copy = new Merge();
                    copy.setStartRow(orig.getStartRow() + rowOffset);
                    copy.setStartCol(orig.getStartCol());
                    copy.setRowSpan(orig.getRowSpan());
                    copy.setColSpan(orig.getColSpan());
                    canvas.merges.add(copy);
                }
                for (Cell tc : blockStaticCells) {
                    Cell copy = new Cell();
                    copy.setRow(tc.getRow() + rowOffset);
                    copy.setCol(tc.getCol());
                    copy.setValue(tc.getValue());
                    copy.setRichText(tc.getRichText());
                    copy.setStyle(tc.getStyle());
                    canvas.cells.put(key(tc.getRow() + rowOffset, tc.getCol()), copy);
                }
            }
        }
    }

    /**
     * 循环块内格子求值：
     * <ul>
     *   <li>值只引用驱动数据集（或纯文本/参数/循环字段）→ 对当前迭代行求值</li>
     *   <li>值引用了其他数据集 → 独立提取该数据集 + 按格子条件过滤（条件可引用循环字段）→ 子查询求值</li>
     * </ul>
     */
    private Object evalLoopCell(CellBinding b, String drivingDatasetId, Map<String, Object> drow, ParamContext ctx) {
        List<FieldRef> refs = new ArrayList<>();
        collectFieldRefs(b.getValue(), refs);
        String other = null;
        for (FieldRef r : refs) {
            if (!r.datasetId().equals(drivingDatasetId)) {
                other = r.datasetId();
                break;
            }
        }
        if (other == null) {
            return engine.eval(b.getValue(), EvalContext.scalar(drow, ctx));
        }
        RawTable f = Operators.filter(extract(other), b.getConditions(), ctx, engine);
        if (b.getValue() instanceof Value.Aggregate) {
            return engine.eval(b.getValue(), EvalContext.aggregate(f.getRows(), ctx));
        }
        Map<String, Object> first = f.getRows().isEmpty() ? null : f.getRows().get(0);
        return engine.eval(b.getValue(), EvalContext.scalar(first, ctx));
    }

    // ============================================================
    // 模板 / 落格 / 输出
    // ============================================================

    /**
     * 渲染画布：轻量中间结构，避免直接操作 Workbook 的复杂性。
     * <ul>
     *   <li>{@code cells} — 输出格子（key = "row:col"），渲染过程中逐步填入</li>
     *   <li>{@code merges} — 合并区域列表（分组列合并、小计行等）</li>
     *   <li>{@code template} — 模板格子（只读），用于样式继承：
     *       动态填入的格子从模板的"声明格"继承样式</li>
     * </ul>
     */
    private static final class Canvas {
        final Map<String, Cell> cells = new LinkedHashMap<>();
        final List<Merge> merges = new ArrayList<>();
        final Map<String, Cell> template = new HashMap<>();
        /** 渲染过程中被动态写入（数据/聚合/汇总/循环）的格子 key，用于区分"已被数据覆盖"与"纯静态残留" */
        final Set<String> dynamic = new HashSet<>();
        // —— 模板的表级布局（行高/列宽/默认尺寸），渲染后需原样带出，否则导出会丢失尺寸 ——
        String sheetId = "sheet1";
        String sheetName = "Sheet1";
        boolean hasTemplate = false;
        double defaultRowHeight = 24;
        double defaultColumnWidth = 88;
        List<Column> columns = new ArrayList<>();
        /** 模板原始行配置（按设计行号），渲染后按带扩展位移落到输出行 */
        final List<Row> templateRows = new ArrayList<>();
    }

    /** 画布坐标 key：row:col */
    private static String key(int row, int col) {
        return row + ":" + col;
    }

    /**
     * 载入模板：将模板 Workbook 的格子和合并信息复制到画布。
     * <p>模板格子同时存入 {@code canvas.cells}（作为初始值）和 {@code canvas.template}
     * （作为样式来源）。这样后续动态填入值时，可以从 template 继承声明格的样式。
     */
    private void seedTemplate(Canvas canvas, Workbook template) {
        if (template == null || template.getSheets() == null || template.getSheets().isEmpty()) {
            return;
        }
        Sheet ts = template.getSheets().get(0);
        // 带出表级布局：sheet 标识、默认行高/列宽、列宽配置、行高配置
        canvas.hasTemplate = true;
        if (ts.getId() != null) canvas.sheetId = ts.getId();
        if (ts.getName() != null) canvas.sheetName = ts.getName();
        canvas.defaultRowHeight = ts.getDefaultRowHeight();
        canvas.defaultColumnWidth = ts.getDefaultColumnWidth();
        if (ts.getColumns() != null) {
            canvas.columns.addAll(ts.getColumns());
        }
        if (ts.getRows() != null) {
            canvas.templateRows.addAll(ts.getRows());
        }
        if (ts.getCells() != null) {
            for (Cell tc : ts.getCells()) {
                canvas.template.put(key(tc.getRow(), tc.getCol()), tc);
                Cell copy = new Cell();
                copy.setRow(tc.getRow());
                copy.setCol(tc.getCol());
                copy.setValue(tc.getValue());
                copy.setRichText(tc.getRichText());
                copy.setStyle(tc.getStyle());
                canvas.cells.put(key(tc.getRow(), tc.getCol()), copy);
            }
        }
        if (ts.getMerges() != null) {
            canvas.merges.addAll(ts.getMerges());
        }
    }

    /**
     * 落格：在画布的 (outRow, outCol) 处填入值，并从模板的"声明格"继承样式。
     *
     * <p>样式继承机制：每个动态格子都有一个 {@code source}（即模型中的 CellRef），
     * 指向模板中的"声明格"——用户在 Univer 里给那个格子设置的样式（字体/边框/颜色等）。
     * 纵向扩展时，多条记录都从同一个声明格继承样式，实现了"样式随行复制"。
     *
     * @param canvas 画布
     * @param source 声明格坐标（用于从 template 中查找样式），可为 null
     * @param outRow 实际输出行号（可能因纵向扩展而偏移）
     * @param outCol 实际输出列号
     * @param value  填入的值（转为 JsonNode 存储）
     */
    private void place(Canvas canvas, CellRef source, int outRow, int outCol, Object value) {
        Cell cell = canvas.cells.get(key(outRow, outCol));
        if (cell == null) {
            cell = new Cell();
            cell.setRow(outRow);
            cell.setCol(outCol);
            canvas.cells.put(key(outRow, outCol), cell);
        }
        cell.setValue(toNode(value));
        // 动态值覆盖任何模板残留的富文本（否则陈旧富文本会盖住数据显示）
        cell.setRichText(null);
        canvas.dynamic.add(key(outRow, outCol));
        if (source != null) {
            Cell src = canvas.template.get(key(source.row(), source.column()));
            if (src != null && src.getStyle() != null) {
                cell.setStyle(src.getStyle());
            }
        }
    }

    private Workbook buildWorkbook(Canvas canvas) {
        int maxRow = 0;
        int maxCol = 0;
        for (Cell c : canvas.cells.values()) {
            maxRow = Math.max(maxRow, c.getRow());
            maxCol = Math.max(maxCol, c.getCol());
        }
        for (Merge m : canvas.merges) {
            maxRow = Math.max(maxRow, m.getStartRow() + m.getRowSpan() - 1);
            maxCol = Math.max(maxCol, m.getStartCol() + m.getColSpan() - 1);
        }

        Sheet sheet = new Sheet();
        sheet.setId(canvas.sheetId);
        sheet.setName(canvas.sheetName);
        sheet.setRowCount(maxRow + 1);
        sheet.setColumnCount(maxCol + 1);
        sheet.setCells(new ArrayList<>(canvas.cells.values()));
        sheet.setMerges(canvas.merges);
        // 带出模板的表级布局：默认行高/列宽、列宽配置、（已按带扩展位移的）行高配置
        if (canvas.hasTemplate) {
            sheet.setDefaultRowHeight(canvas.defaultRowHeight);
            sheet.setDefaultColumnWidth(canvas.defaultColumnWidth);
            if (!canvas.columns.isEmpty()) {
                sheet.setColumns(new ArrayList<>(canvas.columns));
            }
            if (!canvas.templateRows.isEmpty()) {
                sheet.setRows(new ArrayList<>(canvas.templateRows));
            }
        }

        Workbook workbook = new Workbook();
        workbook.setSheets(List.of(sheet));
        return workbook;
    }

    // ============================================================
    // 组合表：提取所有用到的数据集并按关系 join
    // ============================================================

    /**
     * 构建组合表：收集所有 CellBinding 引用的数据集，按 Relationship 链式 join。
     *
     * <h3>算法：贪心 join</h3>
     * <ol>
     *   <li>扫描所有 CellBinding 及其条件，收集用到的 datasetId 集合</li>
     *   <li>取第一个数据集作为起点</li>
     *   <li>反复查找：剩余数据集中，哪个能通过 Relationship 连到已合并的集合</li>
     *   <li>找到就 join 进结果，加入已合并集合，继续查找</li>
     *   <li>找不到则停止（可能缺少关系定义）</li>
     * </ol>
     *
     * <p>这个贪心策略保证了：如果所有需要的数据集之间存在连通的关系路径，
     * 就能把它们全部 join 成一张组合表。
     *
     * @param fieldCells 当前需要渲染的 CellBinding 列表
     * @return 所有数据集 join 后的组合表
     */
    private RawTable buildCombinedTable(List<CellBinding> dataCells) {
        LinkedHashSet<String> needed = new LinkedHashSet<>();
        for (CellBinding b : dataCells) {
            List<FieldRef> refs = new ArrayList<>();
            collectFieldRefs(b.getValue(), refs);
            for (FieldRef r : refs) {
                needed.add(r.datasetId());
            }
            if (b.getConditions() != null) {
                for (Condition c : b.getConditions()) {
                    collectFieldRefs(c.getLeft(), refs);
                    collectFieldRefs(c.getRight(), refs);
                    for (FieldRef r : refs) {
                        needed.add(r.datasetId());
                    }
                }
            }
        }

        List<String> remaining = new ArrayList<>(needed);
        RawTable result = extract(remaining.remove(0));
        Set<String> included = new HashSet<>();
        included.add(needed.iterator().next());

        boolean progress = true;
        while (!remaining.isEmpty() && progress) {
            progress = false;
            for (int i = 0; i < remaining.size(); i++) {
                Relationship rel = findRelation(included, remaining.get(i));
                if (rel != null) {
                    result = Operators.join(result, extract(remaining.get(i)), rel);
                    included.add(remaining.remove(i));
                    progress = true;
                    break;
                }
            }
        }
        return result;
    }

    private Relationship findRelation(Set<String> included, String dsId) {
        if (dm.getRelationships() == null) {
            return null;
        }
        for (Relationship rel : dm.getRelationships()) {
            String a = rel.getLeft().datasetId();
            String b = rel.getRight().datasetId();
            if ((a.equals(dsId) && included.contains(b)) || (b.equals(dsId) && included.contains(a))) {
                return rel;
            }
        }
        return null;
    }

    /**
     * 提取数据集（带缓存）：同一数据集在一次渲染中只提取一次。
     * <p>自动区分普通 Dataset 和 UNION 派生 Dataset：
     * <ul>
     *   <li>{@link TableDataset}：找到 DataSource → 找到匹配的 DataExtractor → 调用 extract()</li>
     *   <li>{@link UnionDataset}：逐成员提取，按映射对齐列名，纵向追加</li>
     * </ul>
     */
    private RawTable extract(String datasetId) {
        RawTable cached = cache.get(datasetId);
        if (cached != null) {
            return cached;
        }
        Dataset ds = dm.getDatasets().stream()
                .filter(d -> d.getId().equals(datasetId)).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "数据集不存在: " + datasetId + "，可用数据集: " +
                        dm.getDatasets().stream().map(Dataset::getId).toList()));

        RawTable result;
        if (ds instanceof UnionDataset u) {
            result = extractUnion(u);
        } else if (ds instanceof TableDataset t) {
            DataSource src = dm.getDatasources().stream()
                    .filter(s -> s.getId().equals(t.getDatasourceId())).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "数据源不存在: " + t.getDatasourceId() + " (数据集: " + datasetId + ")"));
            DataExtractor extractor = extractors.stream()
                    .filter(e -> e.supports(src.getType())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("无提取器支持类型: " + src.getType()));
            result = extractor.extract(src, t);
        } else {
            throw new IllegalStateException("未知数据集类型: " + ds.getClass().getName());
        }
        cache.put(datasetId, result);
        return result;
    }

    /** UNION 派生数据集：逐成员提取，按映射把成员字段对齐到统一列，纵向追加 */
    private RawTable extractUnion(UnionDataset ds) {
        List<String> columns = new ArrayList<>();
        for (Field f : ds.getFields()) {
            columns.add(ds.getId() + "." + f.getName());
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (UnionMember member : ds.getMembers()) {
            RawTable memberTable = extract(member.datasetId());
            for (Map<String, Object> mr : memberTable.getRows()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (Field f : ds.getFields()) {
                    String memberField = member.mapping().get(f.getName());
                    Object v = memberField == null ? null : mr.get(member.datasetId() + "." + memberField);
                    row.put(ds.getId() + "." + f.getName(), v);
                }
                rows.add(row);
            }
        }
        return new RawTable(columns, rows);
    }

    // ============================================================
    // 工具
    // ============================================================

    /** 判断聚合格：值的根节点是 Aggregate */
    private static boolean isAgg(CellBinding b) {
        return b.getValue() instanceof Value.Aggregate;
    }

    /**
     * 取格子值所绑定的字段（分组/明细/聚合的字段定位用）：
     * 值是 {@code FieldValue} 或 {@code Aggregate(FieldValue)} 时返回其 FieldRef，否则 null。
     */
    private static FieldRef fieldOf(CellBinding b) {
        Value v = b.getValue();
        if (v instanceof Value.FieldValue fv) {
            return fv.ref();
        }
        if (v instanceof Value.Aggregate a && a.operand() instanceof Value.FieldValue fv) {
            return fv.ref();
        }
        return null;
    }

    /** 该格子是否需要数据表参与：值引用了字段，或带了过滤条件。 */
    private boolean referencesData(CellBinding b) {
        List<FieldRef> refs = new ArrayList<>();
        collectFieldRefs(b.getValue(), refs);
        return !refs.isEmpty() || (b.getConditions() != null && !b.getConditions().isEmpty());
    }

    /** 递归收集表达式里引用到的所有字段。 */
    private void collectFieldRefs(Value v, List<FieldRef> out) {
        if (v instanceof Value.FieldValue fv) {
            out.add(fv.ref());
        } else if (v instanceof Value.Aggregate a) {
            collectFieldRefs(a.operand(), out);
        } else if (v instanceof Value.Template t) {
            for (Value.Template.Part p : t.parts()) {
                if (p instanceof Value.Template.Hole h) {
                    collectFieldRefs(h.value(), out);
                }
            }
        } else if (v instanceof Value.FunctionCall fc) {
            for (Value a : fc.args()) {
                collectFieldRefs(a, out);
            }
        }
        // Literal / ParamValue / LoopFieldValue / NameRef → 无字段引用
    }

    /** 收集所有格子的条件，合并为一个列表（用于全局过滤） */
    private List<Condition> collectConditions(List<CellBinding> cells) {
        List<Condition> filters = new ArrayList<>();
        for (CellBinding b : cells) {
            if (b.getConditions() != null) {
                filters.addAll(b.getConditions());
            }
        }
        return filters;
    }

    /** 计算分组列在父格链中的深度（0 = 顶层，1 = 子级，2 = 孙级...），用于排序分组列 */
    private int depth(CellBinding gc, Map<CellRef, CellBinding> groupByCell) {
        int d = 0;
        CellRef p = gc.getParentCell();
        while (p != null && groupByCell.containsKey(p)) {
            d++;
            p = groupByCell.get(p).getParentCell();
        }
        return d;
    }

    /** 生成分组排序 key：把一行在各分组列上的值拼接为字符串，用于 Comparator */
    private String tupleKey(Map<String, Object> row, List<CellBinding> groupCols) {
        StringBuilder sb = new StringBuilder();
        for (CellBinding gc : groupCols) {
            sb.append(row.get(fieldOf(gc).qualified())).append('');
        }
        return sb.toString();
    }

    /** 提取一行在各分组列上的值元组，用于分组比较和合并判断 */
    private List<Object> tuple(Map<String, Object> row, List<CellBinding> groupCols) {
        List<Object> t = new ArrayList<>();
        for (CellBinding gc : groupCols) {
            t.add(row.get(fieldOf(gc).qualified()));
        }
        return t;
    }

    /** 每行一个 Unit（有明细列时）：保留全部明细行，不做分组聚合 */
    private List<Unit> oneUnitPerRow(List<Map<String, Object>> rows, List<CellBinding> groupCols) {
        List<Unit> units = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            units.add(new Unit(tuple(r, groupCols), List.of(r)));
        }
        return units;
    }

    /** 按分组元组聚合（无明细列时）：相邻相同分组值的行归入同一 Unit */
    private List<Unit> groupByTuple(List<Map<String, Object>> rows, List<CellBinding> groupCols) {
        List<Unit> units = new ArrayList<>();
        List<Object> curTuple = null;
        List<Map<String, Object>> curRows = null;
        for (Map<String, Object> r : rows) {
            List<Object> t = tuple(r, groupCols);
            if (curTuple == null || !curTuple.equals(t)) {
                curTuple = t;
                curRows = new ArrayList<>();
                units.add(new Unit(t, curRows));
            }
            curRows.add(r);
        }
        return units;
    }

    /**
     * 检测分组断点：两个相邻元组在前 size 个分量中，第一个不同的层级索引。
     * <p>用于判断在哪些层级插入小计行：breakLevel = d 表示第 d 层分组值变了，
     * 需要从最内层到第 d 层都插入小计。都相同返回 -1。
     */
    private int firstDiffLevel(List<Object> a, List<Object> b, int size) {
        for (int d = 0; d < size; d++) {
            if (!Objects.equals(a.get(d), b.get(d))) {
                return d;
            }
        }
        return -1;
    }

    /** 两个元组的前 p 个分量是否相等（用于合并列的连续相同值判断） */
    private boolean prefixEq(List<Object> a, List<Object> b, int p) {
        for (int d = 0; d < p; d++) {
            if (!Objects.equals(a.get(d), b.get(d))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 聚合列的汇总前缀长度：决定聚合范围。
     * <p>如果聚合列有 parentCell 指向某个分组列，则汇总该分组列及以上层级的所有行
     * （如"总人数"按单位汇总，parentCell 指向单位列 → prefixLen = 1）。
     * 未指定 parentCell 则按最细粒度（全部行）。
     */
    private int aggPrefixLen(CellBinding ac, List<CellBinding> groupCols, Map<CellRef, CellBinding> groupByCell) {
        CellRef p = ac.getParentCell();
        if (p != null && groupByCell.containsKey(p)) {
            return depth(groupByCell.get(p), groupByCell) + 1;
        }
        return groupCols.size();
    }

    /** 取出与 target 前 p 个分组分量相同的所有行（用于粗粒度聚合列的数据范围筛选） */
    private List<Map<String, Object>> rowsWithPrefix(List<Map<String, Object>> rows, List<CellBinding> groupCols,
                                                     List<Object> target, int p) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            if (prefixEq(tuple(r, groupCols), target, p)) {
                out.add(r);
            }
        }
        return out;
    }

    /** 展开 details[from..to] 的所有源行（用于小计行的聚合数据范围） */
    private List<Map<String, Object>> flatten(List<Unit> details, int from, int to) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            out.addAll(details.get(i).rows);
        }
        return out;
    }

    /**
     * 合并列：把输出序列中"前 prefixLen 个分组分量相同"的连续明细行合并为跨行单元格。
     * <p>用于 GROUP 列的 mergeRepeated 效果（如"总部"跨 3 行合并）和粗粒度聚合列。
     * 小计/总计行（detail=false）不参与合并。
     */
    private void mergeColumn(Canvas canvas, List<Out> seq, int bandBase, int column, int prefixLen) {
        int i = 0;
        while (i < seq.size()) {
            if (!seq.get(i).detail) {
                i++;
                continue;
            }
            int j = i + 1;
            while (j < seq.size() && seq.get(j).detail
                    && prefixEq(seq.get(j).tuple, seq.get(i).tuple, prefixLen)) {
                j++;
            }
            if (j - i > 1) {
                Merge m = new Merge();
                m.setStartRow(bandBase + i);
                m.setStartCol(column);
                m.setRowSpan(j - i);
                m.setColSpan(1);
                canvas.merges.add(m);
            }
            i = j;
        }
    }

    /**
     * 按指定字段去重（保留首行）：用于循环块的 groupBy 去重迭代。
     * <p>如 groupBy=["dept"]，则"研发/研发/测试"去重为"研发/测试"，循环只迭代两次。
     */
    private RawTable distinctBy(RawTable t, String datasetId, List<String> fields) {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> r : t.getRows()) {
            StringBuilder key = new StringBuilder();
            for (String f : fields) {
                key.append(r.get(datasetId + "." + f)).append('');
            }
            if (seen.add(key.toString())) {
                out.add(r);
            }
        }
        return new RawTable(t.getColumns(), out);
    }

    /**
     * 去除限定名前缀：{@code {"employees.name": "张三"}} → {@code {"name": "张三"}}。
     * <p>循环作用域里的字段名不带 datasetId 前缀（循环只引用一个驱动数据集，字段名天然唯一），
     * 这样 {@link com.codingapi.report.expression.Value.LoopFieldValue} 可以直接用裸字段名引用。
     */
    private Map<String, Object> unqualify(Map<String, Object> row, String datasetId) {
        String prefix = datasetId + ".";
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                out.put(e.getKey().substring(prefix.length()), e.getValue());
            }
        }
        return out;
    }

    /** 判断格子是否在任意循环块范围内（用于 renderFree 跳过循环块内的格子） */
    private boolean inAnyLoop(Report report, CellRef cell) {
        if (report.getLoopBlocks() == null) {
            return false;
        }
        for (LoopBlock loop : report.getLoopBlocks()) {
            if (inLoop(loop, cell)) {
                return true;
            }
        }
        return false;
    }

    /** 判断格子是否在指定循环块的矩形范围内（坐标包含边界） */
    private boolean inLoop(LoopBlock loop, CellRef cell) {
        CellRef s = loop.getStart();
        CellRef e = loop.getEnd();
        return s.sheetId().equals(cell.sheetId())
                && cell.row() >= s.row() && cell.row() <= e.row()
                && cell.column() >= s.column() && cell.column() <= e.column();
    }

    /** 将 Java 值转为 JsonNode 存储到 Cell.value（Number→numberNode, Boolean→booleanNode, 其余→textNode） */
    private JsonNode toNode(Object v) {
        if (v == null) {
            return NF.nullNode();
        }
        if (v instanceof Number n) {
            return NF.numberNode(n.doubleValue());
        }
        if (v instanceof Boolean b) {
            return NF.booleanNode(b);
        }
        return NF.textNode(String.valueOf(v));
    }

    /**
     * 分组单元：一行或多行数据在分组维度上的抽象。
     * <ul>
     *   <li>有明细列时（LIST 模式）：每行一个 Unit，tuple = 该行在各分组列的值，rows = 单行</li>
     *   <li>无明细列时（纯 GROUP 模式）：相邻相同分组值的行归入同一 Unit，rows = 多行</li>
     * </ul>
     */
    private static final class Unit {
        /** 分组元组：该行/该组在各分组列上的值（如 ["总部", "研发"]） */
        final List<Object> tuple;
        /** 该分组单元包含的源数据行 */
        final List<Map<String, Object>> rows;

        Unit(List<Object> tuple, List<Map<String, Object>> rows) {
            this.tuple = tuple;
            this.rows = rows;
        }
    }

    /**
     * 输出序列中的一行：渲染结果的中间表示。
     * <p>输出序列 = 明细行 + 小计行 + 总计行 的有序列表，决定了最终画布中行的排列顺序。
     * <ul>
     *   <li><b>明细行</b>（detail=true）：一条数据记录，携带分组元组用于后续的列合并判断</li>
     *   <li><b>小计/总计行</b>（detail=false）：汇总行，不参与列合并</li>
     * </ul>
     * <p>{@code values}：列号 → 值（只存有值的列，不存的跳过）
     * <p>{@code sources}：列号 → 声明格 CellRef（用于 place 时从模板继承样式）
     */
    private static final class Out {
        final boolean detail;
        /** 分组元组（仅 detail=true 时有值，用于合并列的连续相同值判断） */
        final List<Object> tuple;
        /** 列号 → 显示值 */
        final Map<Integer, Object> values = new LinkedHashMap<>();
        /** 列号 → 声明格坐标（样式继承来源） */
        final Map<Integer, CellRef> sources = new HashMap<>();

        Out(boolean detail, List<Object> tuple) {
            this.detail = detail;
            this.tuple = tuple;
        }
    }
}
