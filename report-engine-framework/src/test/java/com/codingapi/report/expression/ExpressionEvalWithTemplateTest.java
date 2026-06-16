package com.codingapi.report.expression;

import com.codingapi.report.param.ParamContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 端到端集成测试：{@link Templates#parse} 解析 + {@link ExpressionEngine#eval} 求值。
 *
 * <p>验证用户输入的文本表达式经过解析和求值后能产生正确的结果。
 */
@DisplayName("Templates.parse + ExpressionEngine.eval 端到端")
class ExpressionEvalWithTemplateTest {

    private final ExpressionEngine engine = new ExpressionEngine();

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    // ============================================================
    // 标量求值
    // ============================================================

    @Test
    @DisplayName("${name} 通过参数解析 → 李四")
    void nameRefViaParam() {
        Value v = Templates.parse("${name}");
        ParamContext params = new ParamContext(Map.of());
        params.setLoopRow("loop1", Map.of("name", "李四"));
        EvalContext ctx = EvalContext.scalar(null, params);
        // NameRef 查 loopField → "李四"
        // 但 setLoopRow 需要 loopBlockId，用 lookup 查的是 paramName
        // 改用外部参数
        params = new ParamContext(Map.of("name", "李四"));
        ctx = EvalContext.scalar(null, params);
        assertEquals("李四", engine.eval(v, ctx));
    }

    @Test
    @DisplayName("${d.name} 从当前行取值 → 张三")
    void fieldRefScalar() {
        Value v = Templates.parse("${d.name}");
        EvalContext ctx = EvalContext.scalar(row("d.name", "张三"), new ParamContext(Map.of()));
        assertEquals("张三", engine.eval(v, ctx));
    }

    @Test
    @DisplayName("纯文本不走表达式引擎 → 原样返回")
    void literalEval() {
        Value v = Templates.parse("固定标题");
        EvalContext ctx = EvalContext.scalar(null, new ParamContext(Map.of()));
        assertEquals("固定标题", engine.eval(v, ctx));
    }

    // ============================================================
    // 聚合求值
    // ============================================================

    @Test
    @DisplayName("${COUNT(d.name)} 聚合：3 行 → 3")
    void countAggregate() {
        Value v = Templates.parse("${COUNT(d.name)}");
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                row("d.name", "张三"),
                row("d.name", "李四"),
                row("d.name", "王五")));
        EvalContext ctx = EvalContext.aggregate(rows, new ParamContext(Map.of()));
        Object result = engine.eval(v, ctx);
        assertEquals(3L, result);
    }

    @Test
    @DisplayName("${SUM(d.salary)} 聚合：8000+9000+7500 → 24500.0")
    void sumAggregate() {
        Value v = Templates.parse("${SUM(d.salary)}");
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                row("d.salary", 8000.0),
                row("d.salary", 9000.0),
                row("d.salary", 7500.0)));
        EvalContext ctx = EvalContext.aggregate(rows, new ParamContext(Map.of()));
        assertEquals(24500.0, engine.eval(v, ctx));
    }

    @Test
    @DisplayName("${AVG(d.score)} 聚合：80+90+70 → 80.0")
    void avgAggregate() {
        Value v = Templates.parse("${AVG(d.score)}");
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                row("d.score", 80.0),
                row("d.score", 90.0),
                row("d.score", 70.0)));
        EvalContext ctx = EvalContext.aggregate(rows, new ParamContext(Map.of()));
        assertEquals(80.0, engine.eval(v, ctx));
    }

    // ============================================================
    // 文本 + 聚合混合
    // ============================================================

    @Test
    @DisplayName("部门人数为 ${COUNT(d.name)} → 部门人数为 3")
    void textWithCountAggregate() {
        Value v = Templates.parse("部门人数为 ${COUNT(d.name)}");
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                row("d.name", "张三"),
                row("d.name", "李四"),
                row("d.name", "王五")));
        EvalContext ctx = EvalContext.aggregate(rows, new ParamContext(Map.of()));
        assertEquals("部门人数为 3", engine.eval(v, ctx));
    }

    @Test
    @DisplayName("合计 ${SUM(d.salary)} 元 → 合计 24500 元")
    void textWithSumAggregate() {
        Value v = Templates.parse("合计 ${SUM(d.salary)} 元");
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                row("d.salary", 8000.0),
                row("d.salary", 9000.0),
                row("d.salary", 7500.0)));
        EvalContext ctx = EvalContext.aggregate(rows, new ParamContext(Map.of()));
        assertEquals("合计 24500 元", engine.eval(v, ctx));
    }

    @Test
    @DisplayName("${d.name} 的薪资为 ${d.salary} → 张三 的薪资为 8000")
    void textWithMultipleFieldRefs() {
        Value v = Templates.parse("${d.name} 的薪资为 ${d.salary}");
        EvalContext ctx = EvalContext.scalar(
                row("d.name", "张三", "d.salary", 8000.0),
                new ParamContext(Map.of()));
        assertEquals("张三 的薪资为 8000", engine.eval(v, ctx));
    }

    // ============================================================
    // NameRef 在循环上下文中解析字段
    // ============================================================

    @Test
    @DisplayName("${name} 在循环上下文中 → 取循环字段值")
    void nameRefInLoopContext() {
        Value v = Templates.parse("${name}");
        ParamContext params = new ParamContext(Map.of());
        params.setLoopRow("loop1", Map.of("name", "王五"));
        EvalContext ctx = EvalContext.scalar(null, params);
        assertEquals("王五", engine.eval(v, ctx));
    }

    @Test
    @DisplayName("${year}年度报表 → NameRef 解析为报表参数")
    void nameRefAsReportParam() {
        Value v = Templates.parse("${year}年度报表");
        ParamContext params = new ParamContext(Map.of("year", 2026));
        EvalContext ctx = EvalContext.scalar(null, params);
        assertEquals("2026年度报表", engine.eval(v, ctx));
    }
}
