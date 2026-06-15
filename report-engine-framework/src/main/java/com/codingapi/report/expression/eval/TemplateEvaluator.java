package com.codingapi.report.expression.eval;

import com.codingapi.report.expression.EvalContext;
import com.codingapi.report.expression.ExpressionEngine;
import com.codingapi.report.expression.Value;
import com.codingapi.report.expression.ValueEvaluator;

/** {@link Value.Template} 求值：拼接文本片段，表达式洞递归求值后格式化为字符串。 */
public class TemplateEvaluator implements ValueEvaluator {

    @Override
    public boolean supports(Value value) {
        return value instanceof Value.Template;
    }

    @Override
    public Object eval(Value value, EvalContext ctx, ExpressionEngine engine) {
        StringBuilder sb = new StringBuilder();
        for (Value.Template.Part part : ((Value.Template) value).parts()) {
            if (part instanceof Value.Template.Text t) {
                sb.append(t.text());
            } else if (part instanceof Value.Template.Hole h) {
                sb.append(format(engine.eval(h.value(), ctx)));
            }
        }
        return sb.toString();
    }

    /** 标量转字符串：null→空串；整数值的 double 去掉小数点（2026.0 → "2026"）。 */
    private static String format(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
            return String.valueOf((long) n.doubleValue());
        }
        return String.valueOf(v);
    }
}
