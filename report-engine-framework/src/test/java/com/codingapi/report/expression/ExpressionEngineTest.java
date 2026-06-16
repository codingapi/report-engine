package com.codingapi.report.expression;

import com.codingapi.report.data.dataset.FieldRef;

import com.codingapi.report.param.ParamContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("表达式引擎：节点策略求值")
class ExpressionEngineTest {

    private final ExpressionEngine engine = new ExpressionEngine();

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("字面量直接返回")
    void literal() {
        assertEquals(42, engine.eval(new Value.Literal(42), EvalContext.scalar(null, new ParamContext(Map.of()))));
    }

    @Test
    @DisplayName("字段读取：从当前行取限定列")
    void fieldValue() {
        EvalContext ctx = EvalContext.scalar(row("emp.name", "张三"), new ParamContext(Map.of()));
        assertEquals("张三", engine.eval(new Value.FieldValue(new FieldRef("emp", "name")), ctx));
    }

    @Test
    @DisplayName("报表参数与循环字段")
    void paramAndLoop() {
        ParamContext params = new ParamContext(Map.of("year", 2026));
        params.setLoopRow("loop_emp", Map.of("name", "李四"));
        EvalContext ctx = EvalContext.scalar(null, params);
        assertEquals(2026, engine.eval(new Value.ParamValue("year"), ctx));
        assertEquals("李四", engine.eval(new Value.LoopFieldValue("loop_emp", "name"), ctx));
    }

    @Test
    @DisplayName("文本插值：文本 + 字段洞，整数去小数点")
    void template() {
        Value tpl = new Value.Template(List.of(
                new Value.Template.Hole(new Value.FieldValue(new FieldRef("emp", "name"))),
                new Value.Template.Text("：薪资 "),
                new Value.Template.Hole(new Value.FieldValue(new FieldRef("emp", "salary")))));
        EvalContext ctx = EvalContext.scalar(row("emp.name", "张三", "emp.salary", 8000.0), new ParamContext(Map.of()));
        assertEquals("张三：薪资 8000", engine.eval(tpl, ctx));
    }

    @Test
    @DisplayName("聚合：字段快路径")
    void aggregateField() {
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                row("emp.salary", 8000.0), row("emp.salary", 9000.0), row("emp.salary", 7500.0)));
        Value sum = new Value.Aggregate("SUM", new Value.FieldValue(new FieldRef("emp", "salary")));
        assertEquals(24500.0, engine.eval(sum, EvalContext.aggregate(rows, new ParamContext(Map.of()))));
    }

    @Test
    @DisplayName("聚合：任意子表达式走逐行合成临时列")
    void aggregateExpression() {
        List<Map<String, Object>> rows = new ArrayList<>(List.of(row(), row(), row()));
        Value sum = new Value.Aggregate("SUM", new Value.Literal(5));
        assertEquals(15.0, engine.eval(sum, EvalContext.aggregate(rows, new ParamContext(Map.of()))));
    }

    @Test
    @DisplayName("函数：format / date")
    void functions() {
        ParamContext params = new ParamContext(Map.of());
        Value fmt = new Value.FunctionCall("format", List.of(new Value.Literal(1234.5), new Value.Literal("#,##0.00")));
        assertEquals("1,234.50", engine.eval(fmt, EvalContext.scalar(null, params)));

        Value date = new Value.FunctionCall("date", List.of(new Value.Literal("2026-06-15"), new Value.Literal("yyyy/MM/dd")));
        assertEquals("2026/06/15", engine.eval(date, EvalContext.scalar(null, params)));
    }

    @Test
    @DisplayName("未注册函数显式抛异常")
    void unknownFunction() {
        Value call = new Value.FunctionCall("nope", List.of());
        assertThrows(IllegalStateException.class,
                () -> engine.eval(call, EvalContext.scalar(null, new ParamContext(Map.of()))));
    }
}
