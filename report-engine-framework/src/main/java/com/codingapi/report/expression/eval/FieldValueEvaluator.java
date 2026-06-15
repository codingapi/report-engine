package com.codingapi.report.expression.eval;

import com.codingapi.report.expression.EvalContext;
import com.codingapi.report.expression.ExpressionEngine;
import com.codingapi.report.expression.Value;
import com.codingapi.report.expression.ValueEvaluator;

import java.util.Map;

/** {@link Value.FieldValue} 求值：从当前行按限定列名取值，无当前行返回 null。 */
public class FieldValueEvaluator implements ValueEvaluator {

    @Override
    public boolean supports(Value value) {
        return value instanceof Value.FieldValue;
    }

    @Override
    public Object eval(Value value, EvalContext ctx, ExpressionEngine engine) {
        Map<String, Object> row = ctx.getRow();
        return row == null ? null : row.get(((Value.FieldValue) value).ref().qualified());
    }
}
