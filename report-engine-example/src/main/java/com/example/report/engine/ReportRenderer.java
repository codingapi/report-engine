package com.example.report.engine;

import com.codingapi.report.excel.pojo.Cell;
import com.codingapi.report.excel.pojo.Merge;
import com.codingapi.report.excel.pojo.Sheet;
import com.codingapi.report.excel.pojo.Workbook;
import com.example.report.model.DataModel;
import com.example.report.model.Report;
import com.example.report.model.grid.Aggregation;
import com.example.report.model.grid.CellBinding;
import com.example.report.model.grid.CellRef;
import com.example.report.model.grid.Condition;
import com.example.report.model.grid.ExpandMode;
import com.example.report.model.grid.Expansion;
import com.example.report.model.grid.FieldCell;
import com.example.report.model.grid.LoopBlock;
import com.example.report.model.grid.SummaryCell;
import com.example.report.model.grid.SummaryRow;
import com.example.report.model.grid.TextCell;
import com.example.report.model.source.DataSource;
import com.example.report.model.source.Dataset;
import com.example.report.model.source.Field;
import com.example.report.model.source.FieldRef;
import com.example.report.model.source.Query;
import com.example.report.model.source.Relationship;
import com.example.report.model.source.UnionMember;
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
 * 报表渲染器：把"报表配置 + 数据"算成一个 Univer/Excel {@link Workbook}。
 *
 * <p>支持<b>模板覆盖</b>：传入的 {@code template} 是 Univer 画布（携带静态文本、富文本、
 * 样式、边框、合并），渲染时以它为底，把动态值填进去并<b>保留样式</b>；纵向扩展的列表行
 * 会继承"声明格"的样式（边框等随行复制）。这正是"模板层(视觉) + 覆盖层(数据)"分层的落地。
 *
 * <p>覆盖四类结构：简单列表、带合并列表、统计列表(分组+聚合)、循环块(薪资条)。
 * 交叉表横向展开尚未实现（见 README 第七节）。
 */
public class ReportRenderer {

    private static final JsonNodeFactory NF = JsonNodeFactory.instance;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(\\w+)}");

    private final List<DataExtractor> extractors;

    private DataModel dm;
    private final Map<String, RawTable> cache = new HashMap<>();

    public ReportRenderer(List<DataExtractor> extractors) {
        this.extractors = extractors;
    }

    /** 无模板：从零渲染（值无样式） */
    public Workbook render(DataModel dm, Report report, ParamContext ctx) {
        return render(dm, report, ctx, null);
    }

    /** 以 template（Univer 画布）为底渲染，保留其样式/边框/合并/富文本 */
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

    private void renderFree(Report report, ParamContext ctx, Canvas canvas) {
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

        RawTable filtered = null;
        List<FieldCell> band = new ArrayList<>();
        List<FieldCell> singles = new ArrayList<>();
        if (!fieldCells.isEmpty()) {
            RawTable combined = buildCombinedTable(fieldCells);
            filtered = Operators.filter(combined, collectConditions(fieldCells), ctx);
            for (FieldCell fc : fieldCells) {
                (fc.getExpansion() == Expansion.VERTICAL ? band : singles).add(fc);
            }
        }

        // 先渲染纵向带，拿到展开行数 N 与带的起始行
        int n = 0;
        int bandBase = Integer.MAX_VALUE;
        if (!band.isEmpty()) {
            for (FieldCell fc : band) {
                bandBase = Math.min(bandBase, fc.getCell().row());
            }
            n = renderBand(band, report.getSummaries(), filtered, bandBase, canvas);
        }
        final int shift = n > 0 ? n - 1 : 0;
        final int base = bandBase;

        // 带下方的格子随展开下移（合计行等会落在列表之后，位置随数据量自适应）
        for (TextCell tc : textCells) {
            int row = tc.getCell().row() > base ? tc.getCell().row() + shift : tc.getCell().row();
            place(canvas, tc.getCell(), row, tc.getCell().column(), substitute(tc.getTemplate(), ctx));
        }
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

    private void renderLoop(Report report, LoopBlock loop, ParamContext ctx, Canvas canvas) {
        Query q = loop.getSource();
        RawTable driving = Operators.filter(extract(q.getDatasetId()), q.getFilters(), ctx);
        if (q.getGroupBy() != null && !q.getGroupBy().isEmpty()) {
            driving = distinctBy(driving, q.getDatasetId(), q.getGroupBy());
        }

        int height = loop.getEnd().row() - loop.getStart().row() + 1;

        List<CellBinding> blockCells = new ArrayList<>();
        for (CellBinding b : report.getCellBindings()) {
            if (inLoop(loop, b.getCell())) {
                blockCells.add(b);
            }
        }

        for (int i = 0; i < driving.getRows().size(); i++) {
            Map<String, Object> drow = driving.getRows().get(i);
            ctx.setLoopRow(loop.getId(), unqualify(drow, q.getDatasetId()));
            int rowOffset = i * height;

            for (CellBinding b : blockCells) {
                int row = b.getCell().row() + rowOffset;
                int col = b.getCell().column();
                if (b instanceof TextCell tc) {
                    place(canvas, b.getCell(), row, col, substitute(tc.getTemplate(), ctx));
                } else if (b instanceof FieldCell fc) {
                    Object v;
                    if (fc.getField().datasetId().equals(q.getDatasetId())) {
                        v = drow.get(Operators.qualified(fc.getField()));
                    } else {
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

    /** 渲染画布：seeded 模板格 + 动态填入；template 提供样式来源 */
    private static final class Canvas {
        final Map<String, Cell> cells = new LinkedHashMap<>();
        final List<Merge> merges = new ArrayList<>();
        final Map<String, Cell> template = new HashMap<>();
    }

    private static String key(int row, int col) {
        return row + ":" + col;
    }

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

    /** 在 (outRow,outCol) 填值，并从模板的"声明格 source"继承样式（含扩展行复制） */
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

    private static boolean isAgg(FieldCell fc) {
        return fc.getAggregation() != null && fc.getAggregation() != Aggregation.NONE;
    }

    private List<Condition> collectConditions(List<FieldCell> fieldCells) {
        List<Condition> filters = new ArrayList<>();
        for (FieldCell fc : fieldCells) {
            if (fc.getConditions() != null) {
                filters.addAll(fc.getConditions());
            }
        }
        return filters;
    }

    private int depth(FieldCell gc, Map<CellRef, FieldCell> groupByCell) {
        int d = 0;
        CellRef p = gc.getParentCell();
        while (p != null && groupByCell.containsKey(p)) {
            d++;
            p = groupByCell.get(p).getParentCell();
        }
        return d;
    }

    private String tupleKey(Map<String, Object> row, List<FieldCell> groupCols) {
        StringBuilder sb = new StringBuilder();
        for (FieldCell gc : groupCols) {
            sb.append(row.get(Operators.qualified(gc.getField()))).append('');
        }
        return sb.toString();
    }

    private List<Object> tuple(Map<String, Object> row, List<FieldCell> groupCols) {
        List<Object> t = new ArrayList<>();
        for (FieldCell gc : groupCols) {
            t.add(row.get(Operators.qualified(gc.getField())));
        }
        return t;
    }

    private List<Unit> oneUnitPerRow(List<Map<String, Object>> rows, List<FieldCell> groupCols) {
        List<Unit> units = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            units.add(new Unit(tuple(r, groupCols), List.of(r)));
        }
        return units;
    }

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

    /** 两个元组在前 size 个分量里，第一个不同的层级；都相同返回 -1 */
    private int firstDiffLevel(List<Object> a, List<Object> b, int size) {
        for (int d = 0; d < size; d++) {
            if (!Objects.equals(a.get(d), b.get(d))) {
                return d;
            }
        }
        return -1;
    }

    /** 两个元组的前 p 个分量是否相等 */
    private boolean prefixEq(List<Object> a, List<Object> b, int p) {
        for (int d = 0; d < p; d++) {
            if (!Objects.equals(a.get(d), b.get(d))) {
                return false;
            }
        }
        return true;
    }

    /** 聚合列的汇总前缀长度：parentCell 指向的分组列深度 +1；未指定则按最细粒度 */
    private int aggPrefixLen(FieldCell ac, List<FieldCell> groupCols, Map<CellRef, FieldCell> groupByCell) {
        CellRef p = ac.getParentCell();
        if (p != null && groupByCell.containsKey(p)) {
            return depth(groupByCell.get(p), groupByCell) + 1;
        }
        return groupCols.size();
    }

    /** 取出与 target 前 p 个分组分量相同的所有行 */
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

    /** 展开 details[from..to] 的所有源行 */
    private List<Map<String, Object>> flatten(List<Unit> details, int from, int to) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            out.addAll(details.get(i).rows);
        }
        return out;
    }

    /** 把某列中"前 prefixLen 个分组分量相同"的连续明细行合并 */
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

    private boolean inLoop(LoopBlock loop, CellRef cell) {
        CellRef s = loop.getStart();
        CellRef e = loop.getEnd();
        return s.sheetId().equals(cell.sheetId())
                && cell.row() >= s.row() && cell.row() <= e.row()
                && cell.column() >= s.column() && cell.column() <= e.column();
    }

    private Object firstValue(RawTable rows, FieldRef field) {
        if (rows.getRows().isEmpty()) {
            return null;
        }
        return rows.getRows().get(0).get(Operators.qualified(field));
    }

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

    private String formatScalar(Object v) {
        if (v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
            return String.valueOf((long) n.doubleValue());
        }
        return String.valueOf(v);
    }

    private static final class Unit {
        final List<Object> tuple;
        final List<Map<String, Object>> rows;

        Unit(List<Object> tuple, List<Map<String, Object>> rows) {
            this.tuple = tuple;
            this.rows = rows;
        }
    }

    /** 输出序列里的一行：明细行(detail=true，带分组元组用于合并)或小计/总计行 */
    private static final class Out {
        final boolean detail;
        final List<Object> tuple;
        final Map<Integer, Object> values = new LinkedHashMap<>();
        final Map<Integer, CellRef> sources = new HashMap<>();

        Out(boolean detail, List<Object> tuple) {
            this.detail = detail;
            this.tuple = tuple;
        }
    }
}
