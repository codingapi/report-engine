package com.codingapi.report.expression.eval;

import com.codingapi.report.expression.EvalContext;
import com.codingapi.report.expression.ExpressionEngine;
import com.codingapi.report.expression.Value;
import com.codingapi.report.expression.ValueEvaluator;

/** {@link Value.ParamValue} 求值：按名字取报表级参数。 */
public class ParamValueEvaluator implements ValueEvaluator {

    @Override
    public boolean supports(Value value) {
        return value instanceof Value.ParamValue;
    }

    @Override
    public Object eval(Value value, EvalContext ctx, ExpressionEngine engine) {
        return ctx.getParams().external(((Value.ParamValue) value).name());
    }
}
