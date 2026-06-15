package com.codingapi.report.expression.eval;

import com.codingapi.report.expression.EvalContext;
import com.codingapi.report.expression.ExpressionEngine;
import com.codingapi.report.expression.Value;
import com.codingapi.report.expression.ValueEvaluator;

/** {@link Value.LoopFieldValue} 求值：取指定循环块当前迭代行的字段值。 */
public class LoopFieldValueEvaluator implements ValueEvaluator {

    @Override
    public boolean supports(Value value) {
        return value instanceof Value.LoopFieldValue;
    }

    @Override
    public Object eval(Value value, EvalContext ctx, ExpressionEngine engine) {
        Value.LoopFieldValue lf = (Value.LoopFieldValue) value;
        return ctx.getParams().loopField(lf.loopBlockId(), lf.field());
    }
}
