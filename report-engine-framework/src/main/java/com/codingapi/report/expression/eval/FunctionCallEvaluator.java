package com.codingapi.report.expression.eval;

import com.codingapi.report.expression.EvalContext;
import com.codingapi.report.expression.ExpressionEngine;
import com.codingapi.report.expression.Value;
import com.codingapi.report.expression.ValueEvaluator;
import com.codingapi.report.expression.function.Functions;
import java.util.ArrayList;
import java.util.List;

/** {@link Value.FunctionCall} 求值：先对各参数求值，再交给 {@link Functions} 注册表按函数名调用。 */
public class FunctionCallEvaluator implements ValueEvaluator {

    @Override
    public boolean supports(Value value) {
        return value instanceof Value.FunctionCall;
    }

    @Override
    public Object eval(Value value, EvalContext ctx, ExpressionEngine engine) {
        Value.FunctionCall fc = (Value.FunctionCall) value;
        List<Object> args = new ArrayList<>();
        for (Value arg : fc.args()) {
            args.add(engine.eval(arg, ctx));
        }
        return Functions.call(fc.name(), args);
    }
}
