package com.codingapi.report.expression.eval;

import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.expression.EvalContext;
import com.codingapi.report.expression.ExpressionEngine;
import com.codingapi.report.expression.Value;
import com.codingapi.report.expression.ValueEvaluator;
import com.codingapi.report.operator.aggregation.Aggregators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link Value.Aggregate} 求值：在上下文行集合上聚合子表达式的逐行结果，委托 {@link Aggregators}。
 *
 * <ul>
 *   <li>子表达式是 {@link Value.FieldValue}：直接用 {@link Aggregators} 聚合该列（快路径）</li>
 *   <li>子表达式是任意表达式（如 {@code base+bonus}）：逐行求值成临时列再聚合（通用路径）</li>
 * </ul>
 */
public class AggregateEvaluator implements ValueEvaluator {

    /** 通用路径用的临时列引用。 */
    private static final FieldRef TMP = new FieldRef("__expr__", "v");

    @Override
    public boolean supports(Value value) {
        return value instanceof Value.Aggregate;
    }

    @Override
    public Object eval(Value value, EvalContext ctx, ExpressionEngine engine) {
        Value.Aggregate agg = (Value.Aggregate) value;
        List<Map<String, Object>> rows = rowsOf(ctx);

        if (agg.operand() instanceof Value.FieldValue fv) {
            return Aggregators.aggregate(agg.aggregation(), rows, fv.ref());
        }
        List<Map<String, Object>> synthetic = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put(TMP.qualified(), engine.eval(agg.operand(), ctx.withRow(r)));
            synthetic.add(m);
        }
        return Aggregators.aggregate(agg.aggregation(), synthetic, TMP);
    }

    /** 取聚合的行集合：优先 rows，退化到单个当前行，再退化到空。 */
    private static List<Map<String, Object>> rowsOf(EvalContext ctx) {
        if (ctx.getRows() != null) {
            return ctx.getRows();
        }
        return ctx.getRow() != null ? List.of(ctx.getRow()) : List.of();
    }
}
