package com.codingapi.report.expression.eval;

import com.codingapi.report.expression.EvalContext;
import com.codingapi.report.expression.ExpressionEngine;
import com.codingapi.report.expression.Value;
import com.codingapi.report.expression.ValueEvaluator;

/** {@link Value.Literal} 求值：直接返回字面量。 */
public class LiteralEvaluator implements ValueEvaluator {

    @Override
    public boolean supports(Value value) {
        return value instanceof Value.Literal;
    }

    @Override
    public Object eval(Value value, EvalContext ctx, ExpressionEngine engine) {
        return ((Value.Literal) value).value();
    }
}
