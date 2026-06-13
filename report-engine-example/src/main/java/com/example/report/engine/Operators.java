package com.example.report.engine;

import com.example.report.model.grid.Aggregation;
import com.example.report.model.grid.Condition;
import com.example.report.model.source.FieldRef;
import com.example.report.model.source.Relationship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Java 内存算子：报表数据计算（过滤 / 关联 / 聚合）全在这里完成，与数据源无关——
 * 这正是"跨库、跨源类型联合处理"得以成立的地方。
 *
 * <p>最小实现：join 仅 INNER、hash join；比较算子覆盖 EQ/NE/GT/GE/LT/LE，
 * 数字两端按 double 比，否则按字符串比。
 */
public final class Operators {

    private Operators() {
    }

    public static String qualified(FieldRef f) {
        return f.datasetId() + "." + f.field();
    }

    /** 过滤：全部条件 AND */
    public static RawTable filter(RawTable t, List<Condition> conds, ParamContext ctx) {
        if (conds == null || conds.isEmpty()) {
            return t;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : t.getRows()) {
            boolean ok = true;
            for (Condition c : conds) {
                if (!eval(row, c, ctx)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                out.add(row);
            }
        }
        return new RawTable(t.getColumns(), out);
    }

    /** 内连接（hash join）：自动判断关系两端各属于哪张表 */
    public static RawTable join(RawTable left, RawTable right, Relationship rel) {
        String e1 = qualified(rel.getLeft());
        String e2 = qualified(rel.getRight());
        String leftKey = left.getColumns().contains(e1) ? e1 : e2;
        String rightKey = leftKey.equals(e1) ? e2 : e1;

        Map<String, List<Map<String, Object>>> index = new HashMap<>();
        for (Map<String, Object> rr : right.getRows()) {
            index.computeIfAbsent(String.valueOf(rr.get(rightKey)), k -> new ArrayList<>()).add(rr);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> lr : left.getRows()) {
            List<Map<String, Object>> matches = index.get(String.valueOf(lr.get(leftKey)));
            if (matches == null) {
                continue;
            }
            for (Map<String, Object> rr : matches) {
                Map<String, Object> merged = new LinkedHashMap<>(lr);
                merged.putAll(rr);
                out.add(merged);
            }
        }

        List<String> cols = new ArrayList<>(left.getColumns());
        for (String c : right.getColumns()) {
            if (!cols.contains(c)) {
                cols.add(c);
            }
        }
        return new RawTable(cols, out);
    }

    /** 聚合：对一组行的某字段求聚合值 */
    public static Object aggregate(List<Map<String, Object>> rows, FieldRef field, Aggregation agg) {
        String col = qualified(field);
        List<Double> nums = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Double d = toDouble(r.get(col));
            if (d != null) {
                nums.add(d);
            }
        }
        return switch (agg) {
            case COUNT -> (long) rows.size();
            case COUNT_DISTINCT -> rows.stream().map(r -> r.get(col)).distinct().count();
            case SUM -> nums.stream().mapToDouble(Double::doubleValue).sum();
            case AVG -> nums.isEmpty() ? 0.0 : nums.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            case MAX -> nums.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            case MIN -> nums.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            default -> null;
        };
    }

    private static boolean eval(Map<String, Object> row, Condition c, ParamContext ctx) {
        Object l = row.get(qualified(c.getLeft()));
        Object r = ctx.resolve(c.getValue());
        return switch (c.getOperator()) {
            case EQ -> eq(l, r);
            case NE -> !eq(l, r);
            case GT -> cmp(l, r) > 0;
            case GE -> cmp(l, r) >= 0;
            case LT -> cmp(l, r) < 0;
            case LE -> cmp(l, r) <= 0;
            default -> true; // 未实现的算子放行（最小引擎）
        };
    }

    private static boolean eq(Object l, Object r) {
        Double dl = toDouble(l);
        Double dr = toDouble(r);
        if (dl != null && dr != null) {
            return dl.doubleValue() == dr.doubleValue();
        }
        return String.valueOf(l).equals(String.valueOf(r));
    }

    private static int cmp(Object l, Object r) {
        Double dl = toDouble(l);
        Double dr = toDouble(r);
        if (dl != null && dr != null) {
            return Double.compare(dl, dr);
        }
        return String.valueOf(l).compareTo(String.valueOf(r));
    }

    private static Double toDouble(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
