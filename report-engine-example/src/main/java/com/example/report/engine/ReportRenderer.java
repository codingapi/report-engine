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
import com.example.report.model.grid.TextCell;
import com.example.report.model.source.DataSource;
import com.example.report.model.source.Dataset;
import com.example.report.model.source.FieldRef;
import com.example.report.model.source.Query;
import com.example.report.model.source.Relationship;
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
        List<FieldCell> fieldCells = new ArrayList<>();
        for (CellBinding b : report.getCellBindings()) {
            if (inAnyLoop(report, b.getCell())) {
                continue;
            }
            if (b instanceof TextCell tc) {
                place(canvas, tc.getCell(), tc.getCell().row(), tc.getCell().column(), substitute(tc.getTemplate(), ctx));
            } else if (b instanceof FieldCell fc) {
                fieldCells.add(fc);
            }
        }
        if (fieldCells.isEmpty()) {
            return;
        }

        RawTable combined = buildCombinedTable(fieldCells);
        RawTable filtered = Operators.filter(combined, collectConditions(fieldCells), ctx);

        List<FieldCell> band = new ArrayList<>();
        for (FieldCell fc : fieldCells) {
            if (fc.getExpansion() == Expansion.VERTICAL) {
                band.add(fc);
            } else {
                Object v = isAgg(fc)
                        ? Operators.aggregate(filtered.getRows(), fc.getField(), fc.getAggregation())
                        : firstValue(filtered, fc.getField());
                place(canvas, fc.getCell(), fc.getCell().row(), fc.getCell().column(), v);
            }
        }
        if (!band.isEmpty()) {
            renderBand(band, filtered, canvas);
        }
    }

    /** 渲染一条纵向带：分组列(GROUP)、明细列(LIST)、聚合列(agg)。 */
    private void renderBand(List<FieldCell> band, RawTable filtered, Canvas canvas) {
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
        List<Unit> units = hasDetail ? oneUnitPerRow(rows, groupCols) : groupByTuple(rows, groupCols);

        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            for (int d = 0; d < groupCols.size(); d++) {
                FieldCell gc = groupCols.get(d);
                place(canvas, gc.getCell(), gc.getCell().row() + i, gc.getCell().column(), unit.tuple.get(d));
            }
            for (FieldCell lc : listCols) {
                Object v = unit.rows.get(0).get(Operators.qualified(lc.getField()));
                place(canvas, lc.getCell(), lc.getCell().row() + i, lc.getCell().column(), v);
            }
            for (FieldCell ac : aggCols) {
                Object v = Operators.aggregate(unit.rows, ac.getField(), ac.getAggregation());
                place(canvas, ac.getCell(), ac.getCell().row() + i, ac.getCell().column(), v);
            }
        }

        // 合并：分组列 mergeRepeated → 相同前缀元组的连续行合并
        for (int d = 0; d < groupCols.size(); d++) {
            FieldCell gc = groupCols.get(d);
            if (!gc.isMergeRepeated()) {
                continue;
            }
            int runStart = 0;
            for (int i = 1; i <= units.size(); i++) {
                boolean samePrefix = i < units.size() && samePrefix(units.get(i), units.get(runStart), d);
                if (!samePrefix) {
                    int len = i - runStart;
                    if (len > 1) {
                        Merge m = new Merge();
                        m.setStartRow(gc.getCell().row() + runStart);
                        m.setStartCol(gc.getCell().column());
                        m.setRowSpan(len);
                        m.setColSpan(1);
                        canvas.merges.add(m);
                    }
                    runStart = i;
                }
            }
        }
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
        Cell src = canvas.template.get(key(source.row(), source.column()));
        if (src != null && src.getStyle() != null) {
            cell.setStyle(src.getStyle());
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
        return cache.computeIfAbsent(datasetId, id -> {
            Dataset ds = dm.getDatasets().stream()
                    .filter(d -> d.getId().equals(id)).findFirst().orElseThrow();
            DataSource src = dm.getDatasources().stream()
                    .filter(s -> s.getId().equals(ds.getDatasourceId())).findFirst().orElseThrow();
            DataExtractor extractor = extractors.stream()
                    .filter(e -> e.supports(src.getType())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("无提取器支持类型: " + src.getType()));
            return extractor.extract(src, ds);
        });
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

    private boolean samePrefix(Unit a, Unit b, int depth) {
        for (int d = 0; d <= depth; d++) {
            if (!Objects.equals(a.tuple.get(d), b.tuple.get(d))) {
                return false;
            }
        }
        return true;
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
}
