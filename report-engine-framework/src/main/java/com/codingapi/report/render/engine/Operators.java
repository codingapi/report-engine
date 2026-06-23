package com.codingapi.report.render.engine;

import com.codingapi.report.data.datasource.RawTable;
import com.codingapi.report.data.relation.JoinType;
import com.codingapi.report.data.relation.Relationship;
import com.codingapi.report.expression.EvalContext;
import com.codingapi.report.expression.ExpressionEngine;
import com.codingapi.report.operator.condition.Condition;
import com.codingapi.report.operator.condition.ConditionPredicates;
import com.codingapi.report.param.ParamContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内存关系算子：跨数据源的<b>过滤</b>与<b>关联</b>都在这里完成，与数据源类型无关。
 *
 * <h3>核心价值：跨源计算</h3>
 * <p>所有计算都在 Java 内存完成（而非下推到 SQL），才能实现 MySQL 表 JOIN CSV 文件、
 * API JSON JOIN 数据库表等跨源关联。各数据源只需通过 {@code DataExtractor} 提取成统一的
 * {@link RawTable}，之后的过滤/关联由本类处理。
 *
 * <h3>职责边界</h3>
 * <ul>
 *   <li><b>过滤</b>（{@link #filter}）：逐行求值条件，单条件的比较判断委托给
 *       {@link ConditionPredicates}（条件算子注册表）——本类不内嵌任何算子 switch</li>
 *   <li><b>关联</b>（{@link #join}）：两表按 {@link Relationship} 做 hash join，支持 INNER/LEFT/RIGHT/FULL</li>
 *   <li><b>聚合</b>：不在本类，见 {@code operator.aggregation.Aggregators}</li>
 * </ul>
 *
 * <h3>JOIN 语义与方向约定</h3>
 * <p>JOIN 类型取自 {@link Relationship#getJoinType()}（null 视为 INNER）。LEFT/RIGHT 的"保留哪一侧"
 * 相对 {@link #join} 的<b>参数位置</b>：LEFT 保留 {@code left} 参数表（无匹配行补 null），
 * RIGHT 保留 {@code right} 参数表，FULL 保留两侧。不依赖 {@code Relationship.left/right} 端点方向——
 * 调用方（{@code ReportRenderer}）始终以累积表为 {@code left} 参数，语义由此固定。
 *
 * <h3>当前实现的限制</h3>
 * <ul>
 *   <li>JOIN 算法为 hash join（右表建索引，左表探测），适合中小数据量</li>
 *   <li>比较算子的覆盖范围由 {@link ConditionPredicates} 决定（未注册的算子会抛异常）</li>
 * </ul>
 */
public final class Operators {

    private Operators() {
    }

    /**
     * 过滤：从 RawTable 中筛选满足<b>全部</b>条件的行（AND 语义）。
     * <p>每个条件的左右值都由 {@code engine} 对当前行求值（支持字段/字面量/报表参数/循环字段），
     * 比较判断交给 {@link ConditionPredicates}。
     *
     * @param t      输入表
     * @param conds  条件列表（全部 AND），null 或空则不过滤
     * @param ctx    参数上下文（报表参数 + 循环作用域）
     * @param engine 表达式引擎，用于对左右值求值
     * @return 过滤后的新 RawTable（不修改原表）
     */
    public static RawTable filter(RawTable t, List<Condition> conds, ParamContext ctx, ExpressionEngine engine) {
        if (conds == null || conds.isEmpty()) {
            return t;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : t.getRows()) {
            EvalContext ec = EvalContext.scalar(row, ctx);
            boolean ok = true;
            for (Condition c : conds) {
                Object left = engine.eval(c.getLeft(), ec);
                Object right = engine.eval(c.getRight(), ec);
                if (!ConditionPredicates.test(c.getOperator(), left, right)) {
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
     * 连接（hash join）：按 {@link Relationship} 定义的键合并两张表，JOIN 类型由 {@link Relationship#getJoinType()} 决定。
     *
     * <ul>
     *   <li>INNER：只保留两侧匹配上的行（交集）</li>
     *   <li>LEFT：保留 {@code left} 参数表全部行，右表无匹配则补 null</li>
     *   <li>RIGHT：保留 {@code right} 参数表全部行，左表无匹配则补 null</li>
     *   <li>FULL：两侧全部保留，各自无匹配补 null</li>
     * </ul>
     * <p>LEFT/RIGHT 的保留侧相对参数位置（见类注释），不依赖 {@code Relationship} 端点方向。
     *
     * <h3>算法</h3>
     * <ol>
     *   <li>自动判断关系的左右端点各属于哪张表（通过列名是否出现在 columns 中）</li>
     *   <li>用右表建哈希索引（key = join 键的字符串值，value = 行列表，处理一对多）</li>
     *   <li>左表逐行探测索引，匹配到的行合并（左行 + 右行字段 putAll）</li>
     *   <li>LEFT/FULL：左行无匹配时保留并补 null；RIGHT/FULL：循环后输出未匹配的右行并补 null</li>
     * </ol>
     *
     * @param left  左表（LEFT/FULL 的保留侧）
     * @param right 右表（RIGHT/FULL 的保留侧）
     * @param rel   关联关系（左右 FieldRef + JoinType）
     * @return 合并后的 RawTable，列 = 左表列 + 右表列（去重）
     */
    public static RawTable join(RawTable left, RawTable right, Relationship rel) {
        // 自动判断：关系的哪个端点属于左表
        String e1 = rel.getLeft().qualified();
        String e2 = rel.getRight().qualified();
        String leftKey = left.getColumns().contains(e1) ? e1 : e2;
        String rightKey = leftKey.equals(e1) ? e2 : e1;

        JoinType jt = rel.getJoinType();
        boolean leftJoin = jt == JoinType.LEFT;
        boolean rightJoin = jt == JoinType.RIGHT;
        boolean fullJoin = jt == JoinType.FULL;
        boolean keepUnmatchedLeft = leftJoin || fullJoin;
        boolean keepUnmatchedRight = rightJoin || fullJoin;

        // 用右表建哈希索引（处理一对多：同一 join 键可能对应多行）
        Map<String, List<Map<String, Object>>> index = new HashMap<>();
        for (Map<String, Object> rr : right.getRows()) {
            index.computeIfAbsent(String.valueOf(rr.get(rightKey)), k -> new ArrayList<>()).add(rr);
        }

        // 右表行的匹配记录（按对象身份判重），用于 RIGHT/FULL 输出未匹配右行
        Set<Map<String, Object>> matchedRight = keepUnmatchedRight
                ? Collections.newSetFromMap(new IdentityHashMap<>())
                : null;

        // 左表逐行探测索引，匹配行合并
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> lr : left.getRows()) {
            List<Map<String, Object>> matches = index.get(String.valueOf(lr.get(leftKey)));
            if (matches == null) {
                // 左行无匹配：LEFT/FULL 保留并补 null；INNER/RIGHT 丢弃
                if (keepUnmatchedLeft) {
                    out.add(fillNull(lr, right.getColumns()));
                }
                continue;
            }
            for (Map<String, Object> rr : matches) {
                Map<String, Object> merged = new LinkedHashMap<>(lr);
                merged.putAll(rr);
                out.add(merged);
                if (matchedRight != null) {
                    matchedRight.add(rr);
                }
            }
        }

        // RIGHT/FULL：输出未匹配的右行，左表列补 null
        if (matchedRight != null) {
            for (Map<String, Object> rr : right.getRows()) {
                if (matchedRight.contains(rr)) {
                    continue;
                }
                out.add(fillNull(rr, left.getColumns()));
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

    /** 复制 base 行，对 missingColumns 中尚未存在的列补 null（用于外连接无匹配侧）。 */
    private static Map<String, Object> fillNull(Map<String, Object> base, List<String> missingColumns) {
        Map<String, Object> merged = new LinkedHashMap<>(base);
        for (String c : missingColumns) {
            merged.putIfAbsent(c, null);
        }
        return merged;
    }
}
