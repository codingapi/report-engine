package com.codingapi.report.expression.eval;

import com.codingapi.report.expression.EvalContext;
import com.codingapi.report.expression.ExpressionEngine;
import com.codingapi.report.expression.Value;
import com.codingapi.report.expression.ValueEvaluator;

/** {@link Value.NameRef} 求值：按名字就近查找（循环作用域优先，再报表参数）。 */
public class NameRefEvaluator implements ValueEvaluator {

    @Override
    public boolean supports(Value value) {
        return value instanceof Value.NameRef;
    }

    @Override
    public Object eval(Value value, EvalContext ctx, ExpressionEngine engine) {
        return ctx.getParams().lookup(((Value.NameRef) value).name());
    }
}
