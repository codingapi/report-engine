package com.codingapi.report.expression;

import com.codingapi.report.expression.eval.AggregateEvaluator;
import com.codingapi.report.expression.eval.FieldValueEvaluator;
import com.codingapi.report.expression.eval.FunctionCallEvaluator;
import com.codingapi.report.expression.eval.LiteralEvaluator;
import com.codingapi.report.expression.eval.LoopFieldValueEvaluator;
import com.codingapi.report.expression.eval.NameRefEvaluator;
import com.codingapi.report.expression.eval.ParamValueEvaluator;
import com.codingapi.report.expression.eval.TemplateEvaluator;
import java.util.List;

/**
 * 表达式引擎：内置全部 {@link ValueEvaluator} 策略，按 {@link Value} 节点类型选中并求值。
 *
 * <h3>支持哪些表达式是预先约定的</h3>
 *
 * <p>{@link Value} 是密封类型，节点集合封闭；每种节点的求值由 {@link #EVALUATORS} 里登记的策略提供。 求值递归（如 {@code Template}
 * 的洞、{@code Aggregate} 的子表达式）通过把引擎自身回传给策略实现。
 *
 * <p>无状态、可复用。一次渲染创建一个实例（或复用单例）即可。
 */
public final class ExpressionEngine {

    /** 节点求值策略。{@link Value} 新增节点时在此登记对应策略。 */
    private static final List<ValueEvaluator> EVALUATORS =
            List.of(
                    new LiteralEvaluator(),
                    new FieldValueEvaluator(),
                    new ParamValueEvaluator(),
                    new LoopFieldValueEvaluator(),
                    new NameRefEvaluator(),
                    new TemplateEvaluator(),
                    new AggregateEvaluator(),
                    new FunctionCallEvaluator());

    /**
     * 对表达式求值。
     *
     * @throws IllegalStateException 当没有策略支持该节点类型
     */
    public Object eval(Value value, EvalContext ctx) {
        for (ValueEvaluator e : EVALUATORS) {
            if (e.supports(value)) {
                return e.eval(value, ctx, this);
            }
        }
        throw new IllegalStateException("不支持的表达式类型: " + value.getClass().getSimpleName());
    }
}
