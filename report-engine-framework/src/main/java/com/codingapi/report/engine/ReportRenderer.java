package com.codingapi.report.engine;

import com.codingapi.report.excel.pojo.Cell;
import com.codingapi.report.excel.pojo.Merge;
import com.codingapi.report.excel.pojo.Sheet;
import com.codingapi.report.excel.pojo.Workbook;
import com.codingapi.report.model.DataModel;
import com.codingapi.report.model.Report;
import com.codingapi.report.model.grid.Aggregation;
import com.codingapi.report.model.grid.CellBinding;
import com.codingapi.report.model.grid.CellRef;
import com.codingapi.report.model.grid.Condition;
import com.codingapi.report.model.grid.ExpandMode;
import com.codingapi.report.model.grid.Expansion;
import com.codingapi.report.model.grid.FieldCell;
import com.codingapi.report.model.grid.LoopBlock;
import com.codingapi.report.model.grid.SummaryCell;
import com.codingapi.report.model.grid.SummaryRow;
import com.codingapi.report.model.grid.TextCell;
import com.codingapi.report.model.source.DataSource;
import com.codingapi.report.model.source.Dataset;
import com.codingapi.report.model.source.Field;
import com.codingapi.report.model.source.FieldRef;
import com.codingapi.report.model.source.Query;
import com.codingapi.report.model.source.Relationship;
import com.codingapi.report.model.source.UnionMember;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 *         b. 逐次迭代：更新 ParamContext → 渲染块内格子（TextCell 替换、FieldCell 取数）
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
    /** 匹配文本占位符的正则：${name} 格式，捕获 name 部分 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(\\w+)}");

    /** 注册的提取器列表，每种 DataSourceType 一个实现 */
    private final List<DataExtractor> extractors;

    /** 当前渲染使用的数据模型（render 时设置，render 结束后不再使用） */
    private DataModel dm;
    /** 数据集提取缓存：同一数据集在一次渲染中只提取一次，后续直接复用 */
    private final Map<String, RawTable> cache = new HashMap<>();

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
     *   <li>分类：将 cellBindings 分为 TextCell（文本占位）和 FieldCell（数据绑定），
     *       跳过属于循环块范围内的格子（那些由 {@link #renderLoop} 处理）</li>
     *   <li>提取 + JOIN：收集所有 FieldCell 引用的数据集，按 Relationship 链式 join 成组合表</li>
     *   <li>过滤：收集所有格子的条件，AND 求解后过滤组合表</li>
     *   <li>分组：FieldCell 按 expansion 分为纵向带（VERTICAL）和单值格（NONE/HORIZONTAL）</li>
     *   <li>纵向带渲染：先渲染带（产出 N 行），计算 shift = N - 1（模板里带占 1 行，实际展开 N 行）</li>
     *   <li>其他格子：带下方的格子（如总计行、标题等）坐标下移 shift 行，自适应数据量</li>
     * </ol>
     *
     * <h3>行偏移的自适应</h3>
     * <p>模板里纵向带只占 1 行（声明行），实际数据可能有 N 行。shift = N - 1 就是
     * "多出来的行数"。模板里位于带下方的格子（row > bandBase）需要下移 shift 行，
     * 这样无论数据量怎么变，下方的总计行、页脚等始终紧跟在数据之后。
     */
    private void renderFree(Report report, ParamContext ctx, Canvas canvas) {
        // 1. 分类：文本格子 vs 数据格子，跳过循环块内的
        List<TextCell> textCells = new ArrayList<>();
        List<FieldCell> fieldCells = new ArrayList<>();
        for (CellBinding b : report.getCellBindings()) {
            if (inAnyLoop(report, b.getCell())) {
                continue;
            }
            if (b instanceof TextCell tc) {
                textCells.add(tc);
            } else if (b instanceof FieldCell fc) {
                fieldCells.add(fc);
            }
        }

        // 2. 提取 + JOIN + 过滤 + 分组
        RawTable filtered = null;
        List<FieldCell> band = new ArrayList<>();     // 纵向扩展的格子（一行一条记录）
        List<FieldCell> singles = new ArrayList<>();  // 不扩展的格子（聚合单值或首值）
        if (!fieldCells.isEmpty()) {
            RawTable combined = buildCombinedTable(fieldCells);
            filtered = Operators.filter(combined, collectConditions(fieldCells), ctx);
            for (FieldCell fc : fieldCells) {
                (fc.getExpansion() == Expansion.VERTICAL ? band : singles).add(fc);
            }
        }

        // 3. 先渲染纵向带，拿到展开行数 N 与带的起始行
        int n = 0;
        int bandBase = Integer.MAX_VALUE;
        if (!band.isEmpty()) {
            for (FieldCell fc : band) {
                bandBase = Math.min(bandBase, fc.getCell().row());
            }
            n = renderBand(band, report.getSummaries(), filtered, bandBase, canvas);
        }
        final int shift = n > 0 ? n - 1 : 0;  // 纵向带多出来的行数
        final int base = bandBase;

        // 4. 文本格子：占位符替换，带下方的行下移 shift
        for (TextCell tc : textCells) {
            int row = tc.getCell().row() > base ? tc.getCell().row() + shift : tc.getCell().row();
            place(canvas, tc.getCell(), row, tc.getCell().column(), substitute(tc.getTemplate(), ctx));
        }
        // 5. 单值格子：聚合或取首值，带下方的行下移 shift
        for (FieldCell fc : singles) {
            Object v = isAgg(fc)
                    ? Operators.aggregate(filtered.getRows(), fc.getField(), fc.getAggregation())
                    : firstValue(filtered, fc.getField());
            int row = fc.getCell().row() > base ? fc.getCell().row() + shift : fc.getCell().row();
            place(canvas, fc.getCell(), row, fc.getCell().column(), v);
        }
    }

    /**
     * 渲染一条纵向带，游标 + 控制断点：
     * 明细行按分组排序输出；分组断点处插入对应层级的小计行；末尾插入总计行。
     * 行位置由游标决定（小计/总计会把后续行下推）。返回产出的总行数。
     *
     * <p>聚合列（agg）按 parentCell 决定汇总层级：parent 指向粗粒度分组列 → 跨该组明细行合并
     * （如"总人数"按单位汇总、跨部门行合并）；不指定则按最细粒度逐行算。
     */
    private int renderBand(List<FieldCell> band, List<SummaryRow> summaries, RawTable filtered,
                           int bandBase, Canvas canvas) {
        List<FieldCell> groupCols = new ArrayList<>();
        List<FieldCell> listCols = new ArrayList<>();
        List<FieldCell> aggCols = new ArrayList<>();
        for (FieldCell fc : band) {
            if (isAgg(fc)) {
                aggCols.add(fc);
            } else if (fc.getExpandMode() == ExpandMode.GROUP) {
                groupCols.add(fc);
            } else {
                listCols.add(fc);
            }
        }

        Map<CellRef, FieldCell> groupByCell = new HashMap<>();
        for (FieldCell gc : groupCols) {
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
                        if (groupCols.get(d).getField().equals(s.getGroupBy())) {
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
            seq.add(detailOut(details.get(i), groupCols, listCols, aggCols, rows, groupByCell));
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
                            seq.add(summaryOut(s, groupRows, groupVal));
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
                seq.add(summaryOut(s, rows, null));
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
            FieldCell gc = groupCols.get(d);
            if (gc.isMergeRepeated()) {
                mergeColumn(canvas, seq, bandBase, gc.getCell().column(), d + 1);
            }
        }
        // 粗粒度聚合列合并（如总人数按单位跨行合并）
        for (FieldCell ac : aggCols) {
            int p = aggPrefixLen(ac, groupCols, groupByCell);
            if (ac.isMergeRepeated() && p < groupCols.size()) {
                mergeColumn(canvas, seq, bandBase, ac.getCell().column(), p);
            }
        }
        return seq.size();
    }

    private Out detailOut(Unit unit, List<FieldCell> groupCols, List<FieldCell> listCols,
                          List<FieldCell> aggCols, List<Map<String, Object>> rows,
                          Map<CellRef, FieldCell> groupByCell) {
        Out o = new Out(true, unit.tuple);
        for (int d = 0; d < groupCols.size(); d++) {
            FieldCell gc = groupCols.get(d);
            o.values.put(gc.getCell().column(), unit.tuple.get(d));
            o.sources.put(gc.getCell().column(), gc.getCell());
        }
        for (FieldCell lc : listCols) {
            o.values.put(lc.getCell().column(), unit.rows.get(0).get(Operators.qualified(lc.getField())));
            o.sources.put(lc.getCell().column(), lc.getCell());
        }
        for (FieldCell ac : aggCols) {
            int p = aggPrefixLen(ac, groupCols, groupByCell);
            List<Map<String, Object>> groupRows = rowsWithPrefix(rows, groupCols, unit.tuple, p);
            o.values.put(ac.getCell().column(), Operators.aggregate(groupRows, ac.getField(), ac.getAggregation()));
            o.sources.put(ac.getCell().column(), ac.getCell());
        }
        return o;
    }

    private Out summaryOut(SummaryRow s, List<Map<String, Object>> groupRows, Object groupVal) {
        Out o = new Out(false, null);
        for (SummaryCell sc : s.getCells()) {
            Object v = sc.getLabel() != null
                    ? sc.getLabel().replace("${group}", groupVal == null ? "" : String.valueOf(groupVal))
                    : Operators.aggregate(groupRows, sc.getField(), sc.getAggregation());
            o.values.put(sc.getColumn(), v);
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
     *         <li>TextCell：占位符替换（循环字段优先于报表参数）</li>
     *         <li>FieldCell 绑定驱动数据集的字段：直接从当前迭代行取值</li>
     *         <li>FieldCell 绑定其他数据集的字段：独立提取 + 按条件过滤（条件里可引用循环字段）+ 取首值或聚合</li>
     *       </ul></li>
     * </ol>
     *
     * <h3>跨数据集取数（子查询模式）</h3>
     * <p>当循环块内的 FieldCell 绑定的是非驱动数据集时（如循环员工，但格子显示的是该员工的学历），
     * 引擎独立提取该数据集并按格子的 conditions 过滤。条件右值可以引用循环字段
     * （{@link com.codingapi.report.model.param.ValueRef.LoopField}），
     * 实现"父迭代传键 → 子查询"的效果。
     */
    private void renderLoop(Report report, LoopBlock loop, ParamContext ctx, Canvas canvas) {
        Query q = loop.getSource();
        // 1. 提取驱动数据集 + 过滤
        RawTable driving = Operators.filter(extract(q.getDatasetId()), q.getFilters(), ctx);
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

        // 5. 逐次迭代
        for (int i = 0; i < driving.getRows().size(); i++) {
            Map<String, Object> drow = driving.getRows().get(i);
            // 更新循环作用域：去掉限定名前缀（循环内字段名天然唯一）
            ctx.setLoopRow(loop.getId(), unqualify(drow, q.getDatasetId()));
            int rowOffset = i * height;  // 每次迭代的行偏移

            for (CellBinding b : blockCells) {
                int row = b.getCell().row() + rowOffset;
                int col = b.getCell().column();
                if (b instanceof TextCell tc) {
                    // 文本占位替换：循环字段优先，报表参数兜底
                    place(canvas, b.getCell(), row, col, substitute(tc.getTemplate(), ctx));
                } else if (b instanceof FieldCell fc) {
                    Object v;
                    if (fc.getField().datasetId().equals(q.getDatasetId())) {
                        // 驱动数据集字段：直接从当前迭代行取
                        v = drow.get(Operators.qualified(fc.getField()));
                    } else {
                        // 其他数据集：独立提取 + 条件过滤（条件可引用循环字段）
                        RawTable f = Operators.filter(extract(fc.getField().datasetId()), fc.getConditions(), ctx);
                        v = isAgg(fc)
                                ? Operators.aggregate(f.getRows(), fc.getField(), fc.getAggregation())
                                : firstValue(f, fc.getField());
                    }
                    place(canvas, b.getCell(), row, col, v);
                }
            }
        }
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
        sheet.setId("sheet1");
        sheet.setName("Sheet1");
        sheet.setRowCount(maxRow + 1);
        sheet.setColumnCount(maxCol + 1);
        sheet.setCells(new ArrayList<>(canvas.cells.values()));
        sheet.setMerges(canvas.merges);

        Workbook workbook = new Workbook();
        workbook.setSheets(List.of(sheet));
        return workbook;
    }

    // ============================================================
    // 组合表：提取所有用到的数据集并按关系 join
    // ============================================================

    /**
     * 构建组合表：收集所有 FieldCell 引用的数据集，按 Relationship 链式 join。
     *
     * <h3>算法：贪心 join</h3>
     * <ol>
     *   <li>扫描所有 FieldCell 及其条件，收集用到的 datasetId 集合</li>
     *   <li>取第一个数据集作为起点</li>
     *   <li>反复查找：剩余数据集中，哪个能通过 Relationship 连到已合并的集合</li>
     *   <li>找到就 join 进结果，加入已合并集合，继续查找</li>
     *   <li>找不到则停止（可能缺少关系定义）</li>
     * </ol>
     *
     * <p>这个贪心策略保证了：如果所有需要的数据集之间存在连通的关系路径，
     * 就能把它们全部 join 成一张组合表。
     *
     * @param fieldCells 当前需要渲染的 FieldCell 列表
     * @return 所有数据集 join 后的组合表
     */
    private RawTable buildCombinedTable(List<FieldCell> fieldCells) {
        LinkedHashSet<String> needed = new LinkedHashSet<>();
        for (FieldCell fc : fieldCells) {
            needed.add(fc.getField().datasetId());
            if (fc.getConditions() != null) {
                for (Condition c : fc.getConditions()) {
                    needed.add(c.getLeft().datasetId());
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
     *   <li>普通 Dataset：找到 DataSource → 找到匹配的 DataExtractor → 调用 extract()</li>
     *   <li>UNION Dataset：逐成员提取，按映射对齐列名，纵向追加</li>
     * </ul>
     */
    private RawTable extract(String datasetId) {
        RawTable cached = cache.get(datasetId);
        if (cached != null) {
            return cached;
        }
        Dataset ds = dm.getDatasets().stream()
                .filter(d -> d.getId().equals(datasetId)).findFirst().orElseThrow();

        RawTable result;
        if (ds.getUnion() != null && !ds.getUnion().isEmpty()) {
            result = extractUnion(ds);
        } else {
            DataSource src = dm.getDatasources().stream()
                    .filter(s -> s.getId().equals(ds.getDatasourceId())).findFirst().orElseThrow();
            DataExtractor extractor = extractors.stream()
                    .filter(e -> e.supports(src.getType())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("无提取器支持类型: " + src.getType()));
            result = extractor.extract(src, ds);
        }
        cache.put(datasetId, result);
        return result;
    }

    /** UNION 派生数据集：逐成员提取，按映射把成员字段对齐到统一列，纵向追加 */
    private RawTable extractUnion(Dataset ds) {
        List<String> columns = new ArrayList<>();
        for (Field f : ds.getFields()) {
            columns.add(ds.getId() + "." + f.getName());
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (UnionMember member : ds.getUnion()) {
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

    /** 判断 FieldCell 是否为聚合格（aggregation 非空且非 NONE） */
    private static boolean isAgg(FieldCell fc) {
        return fc.getAggregation() != null && fc.getAggregation() != Aggregation.NONE;
    }

    /** 收集所有 FieldCell 的条件，合并为一个列表（用于全局过滤） */
    private List<Condition> collectConditions(List<FieldCell> fieldCells) {
        List<Condition> filters = new ArrayList<>();
        for (FieldCell fc : fieldCells) {
            if (fc.getConditions() != null) {
                filters.addAll(fc.getConditions());
            }
        }
        return filters;
    }

    /** 计算分组列在父格链中的深度（0 = 顶层，1 = 子级，2 = 孙级...），用于排序分组列 */
    private int depth(FieldCell gc, Map<CellRef, FieldCell> groupByCell) {
        int d = 0;
        CellRef p = gc.getParentCell();
        while (p != null && groupByCell.containsKey(p)) {
            d++;
            p = groupByCell.get(p).getParentCell();
        }
        return d;
    }

    /** 生成分组排序 key：把一行在各分组列上的值拼接为字符串，用于 Comparator */
    private String tupleKey(Map<String, Object> row, List<FieldCell> groupCols) {
        StringBuilder sb = new StringBuilder();
        for (FieldCell gc : groupCols) {
            sb.append(row.get(Operators.qualified(gc.getField()))).append('');
        }
        return sb.toString();
    }

    /** 提取一行在各分组列上的值元组，用于分组比较和合并判断 */
    private List<Object> tuple(Map<String, Object> row, List<FieldCell> groupCols) {
        List<Object> t = new ArrayList<>();
        for (FieldCell gc : groupCols) {
            t.add(row.get(Operators.qualified(gc.getField())));
        }
        return t;
    }

    /** 每行一个 Unit（有明细列时）：保留全部明细行，不做分组聚合 */
    private List<Unit> oneUnitPerRow(List<Map<String, Object>> rows, List<FieldCell> groupCols) {
        List<Unit> units = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            units.add(new Unit(tuple(r, groupCols), List.of(r)));
        }
        return units;
    }

    /** 按分组元组聚合（无明细列时）：相邻相同分组值的行归入同一 Unit */
    private List<Unit> groupByTuple(List<Map<String, Object>> rows, List<FieldCell> groupCols) {
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
    private int aggPrefixLen(FieldCell ac, List<FieldCell> groupCols, Map<CellRef, FieldCell> groupByCell) {
        CellRef p = ac.getParentCell();
        if (p != null && groupByCell.containsKey(p)) {
            return depth(groupByCell.get(p), groupByCell) + 1;
        }
        return groupCols.size();
    }

    /** 取出与 target 前 p 个分组分量相同的所有行（用于粗粒度聚合列的数据范围筛选） */
    private List<Map<String, Object>> rowsWithPrefix(List<Map<String, Object>> rows, List<FieldCell> groupCols,
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
     * 这样 ValueRef.LoopField 可以直接用裸字段名引用。
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

    /** 取首行的字段值（非聚合单值格的兜底取法：数据集只有一行或只需要第一行时） */
    private Object firstValue(RawTable rows, FieldRef field) {
        if (rows.getRows().isEmpty()) {
            return null;
        }
        return rows.getRows().get(0).get(Operators.qualified(field));
    }

    /**
     * 文本占位替换：将模板中的 ${name} 替换为 ParamContext 中的值。
     * <p>例：{@code "${year}年度报表"} → {@code "2026年度报表"}
     * 找不到值的占位替换为空字符串。整数值的 double 去掉小数点（如 2026.0 → "2026"）。
     */
    private String substitute(String template, ParamContext ctx) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object v = ctx.lookup(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? "" : formatScalar(v)));
        }
        m.appendTail(sb);
        return sb.toString();
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

    /** 格式化标量为字符串（用于占位替换）：整数值的 double 去掉小数点（2026.0 → "2026"） */
    private String formatScalar(Object v) {
        if (v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
            return String.valueOf((long) n.doubleValue());
        }
        return String.valueOf(v);
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
