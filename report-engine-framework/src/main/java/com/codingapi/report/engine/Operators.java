package com.codingapi.report.engine;

import com.codingapi.report.model.grid.Aggregation;
import com.codingapi.report.model.grid.Condition;
import com.codingapi.report.model.source.FieldRef;
import com.codingapi.report.model.source.Relationship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Java 内存算子集合：报表的全部数据计算（过滤 / 关联 / 聚合）都在这里完成，与数据源无关。
 *
 * <h3>核心价值：跨源计算</h3>
 * <p>正因为所有计算都在 Java 内存完成（而非下推到 SQL），才能实现：
 * <ul>
 *   <li>MySQL 表 JOIN CSV 文件</li>
 *   <li>API 返回的 JSON JOIN 数据库表</li>
 *   <li>不同数据库（MySQL + PostgreSQL）之间的关联</li>
 * </ul>
 * 各数据源只需通过 {@link DataExtractor} 提取成统一的 {@link RawTable}，
 * 之后的全部计算由本类处理。
 *
 * <h3>三大算子</h3>
 * <ul>
 *   <li>{@link #filter(RawTable, List, ParamContext)} — 过滤：从行集合中筛选满足全部条件的行</li>
 *   <li>{@link #join(RawTable, RawTable, com.codingapi.report.model.source.Relationship)} — 关联：
 *       两张表按 Relationship 定义的键做 INNER JOIN（hash join）</li>
 *   <li>{@link #aggregate(List, FieldRef, Aggregation)} — 聚合：对一组行的某字段做 SUM/COUNT/AVG 等</li>
 * </ul>
 *
 * <h3>当前实现的限制（最小引擎）</h3>
 * <ul>
 *   <li>JOIN 仅实现 INNER JOIN（LEFT/RIGHT/FULL 预留了枚举但未实现）</li>
 *   <li>JOIN 算法为 hash join（小表建索引，大表探测），适合中小数据量</li>
 *   <li>比较算子覆盖 EQ/NE/GT/GE/LT/LE（LIKE/IN/BETWEEN 等未实现，遇到时放行）</li>
 *   <li>数值比较统一用 double（精度足够报表场景），非数值退化为字符串比较</li>
 * </ul>
 */
public final class Operators {

    private Operators() {
    }

    /**
     * 将 FieldRef 转为限定列名，用于在 RawTable 的行 Map 中查找值。
     * <p>{@code FieldRef("employees", "name")} → {@code "employees.name"}
     */
    public static String qualified(FieldRef f) {
        return f.datasetId() + "." + f.field();
    }

    /**
     * 过滤：从 RawTable 中筛选满足<b>全部</b>条件的行（AND 语义）。
     * <p>每个条件通过 {@link ParamContext#resolve(com.codingapi.report.model.param.ValueRef)}
     * 求解右值，支持字面量、报表参数、循环字段三种来源。
     *
     * @param t     输入表
     * @param conds 条件列表（全部 AND），null 或空则不过滤
     * @param ctx   参数上下文，用于求解条件右值
     * @return 过滤后的新 RawTable（不修改原表）
     */
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

    /**
     * 内连接（hash join）：按 {@link com.codingapi.report.model.source.Relationship} 定义的
     * 左右键合并两张表，只保留两侧都能匹配上的行。
     *
     * <h3>算法：hash join</h3>
     * <ol>
     *   <li>自动判断关系的左右端点各属于哪张表（通过列名是否出现在 columns 列表中）</li>
     *   <li>用右表建哈希索引（key = join 键的字符串值，value = 行列表）</li>
     *   <li>左表逐行探测索引，匹配到的行合并（左行 + 右行字段 putAll）</li>
     * </ol>
     *
     * <h3>自动端点判断</h3>
     * <p>Relationship 的 left/right 是声明式的（可能 left 属于右表），
     * 这里通过检查 {@code left.getColumns().contains(e1)} 自动判断哪个端点对应左表。
     * 这样 Relationship 的定义不需要区分"谁是左表谁是右表"。
     *
     * @param left  左表
     * @param right 右表
     * @param rel   关联关系（左右 FieldRef + JoinType）
     * @return 合并后的 RawTable，列 = 左表列 + 右表列（去重）
     */
    public static RawTable join(RawTable left, RawTable right, Relationship rel) {
        // 自动判断：关系的哪个端点属于左表
        String e1 = qualified(rel.getLeft());
        String e2 = qualified(rel.getRight());
        String leftKey = left.getColumns().contains(e1) ? e1 : e2;
        String rightKey = leftKey.equals(e1) ? e2 : e1;

        // 用右表建哈希索引（处理一对多：同一 join 键可能对应多行）
        Map<String, List<Map<String, Object>>> index = new HashMap<>();
        for (Map<String, Object> rr : right.getRows()) {
            index.computeIfAbsent(String.valueOf(rr.get(rightKey)), k -> new ArrayList<>()).add(rr);
        }

        // 左表逐行探测索引，匹配行合并
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

        // 合并列列表（右表列追加到左表列后，去重）
        List<String> cols = new ArrayList<>(left.getColumns());
        for (String c : right.getColumns()) {
            if (!cols.contains(c)) {
                cols.add(c);
            }
        }
        return new RawTable(cols, out);
    }

    /**
     * 聚合：对一组行的某字段求聚合值。
     *
     * <h3>支持的聚合方式</h3>
     * <ul>
     *   <li>{@code COUNT} — 统计行数（含 null 行）</li>
     *   <li>{@code COUNT_DISTINCT} — 统计不重复值个数</li>
     *   <li>{@code SUM/AVG/MAX/MIN} — 数值聚合（非数值行跳过，null 值跳过）</li>
     *   <li>{@code NONE} — 不聚合，返回 null</li>
     * </ul>
     *
     * <h3>调用场景</h3>
     * <ul>
     *   <li>FieldCell 的 aggregation：格子级别的单值聚合</li>
     *   <li>SummaryCell 的 aggregation：小计/总计行的汇总计算</li>
     * </ul>
     *
     * @param rows  参与聚合的行集合（通常已经过过滤或分组）
     * @param field 聚合目标字段
     * @param agg   聚合方式
     * @return 聚合结果（Number 或 Long），无数据时返回 0 或 null
     */
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

    /**
     * 求解单个条件：从行中取左值，从上下文求解右值，按算子比较。
     * <p>未实现的算子（LIKE/IN/BETWEEN 等）当前放行（返回 true），
     * 属于最小引擎的渐进实现策略——不影响已实现的算子，新算子按需扩展。
     */
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

    /**
     * 相等比较：优先按数值比（两端都能转 double 时），否则按字符串比。
     * <p>这使 "1" == 1、"3.0" == 3 这类跨类型比较也能正确工作。
     */
    private static boolean eq(Object l, Object r) {
        Double dl = toDouble(l);
        Double dr = toDouble(r);
        if (dl != null && dr != null) {
            return dl.doubleValue() == dr.doubleValue();
        }
        return String.valueOf(l).equals(String.valueOf(r));
    }

    /** 大小比较：优先按数值比，否则按字符串字典序比。用于 GT/GE/LT/LE 算子 */
    private static int cmp(Object l, Object r) {
        Double dl = toDouble(l);
        Double dr = toDouble(r);
        if (dl != null && dr != null) {
            return Double.compare(dl, dr);
        }
        return String.valueOf(l).compareTo(String.valueOf(r));
    }

    /**
     * 安全转 Double：Number 类型直接取值，字符串尝试解析，失败返回 null。
     * <p>这是 eq/cmp/aggregate 的基础——统一的数值转换让跨类型比较和计算成为可能。
     */
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
