package com.codingapi.report.expression;

import static org.junit.jupiter.api.Assertions.*;

import com.codingapi.report.data.dataset.FieldRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Templates.parse：文本插值解析器")
class TemplatesTest {

    // ============================================================
    // 纯文本
    // ============================================================

    @Test
    @DisplayName("纯文本 → Literal")
    void plainText() {
        Value v = Templates.parse("商品清单");
        assertInstanceOf(Value.Literal.class, v);
        assertEquals("商品清单", ((Value.Literal) v).value());
    }

    @Test
    @DisplayName("空字符串 → Literal")
    void emptyText() {
        Value v = Templates.parse("");
        assertInstanceOf(Value.Literal.class, v);
        assertEquals("", ((Value.Literal) v).value());
    }

    // ============================================================
    // 简单名字引用（向后兼容）
    // ============================================================

    @Test
    @DisplayName("${name} → NameRef")
    void nameRef() {
        Value v = Templates.parse("${name}");
        assertInstanceOf(Value.NameRef.class, v);
        assertEquals("name", ((Value.NameRef) v).name());
    }

    @Test
    @DisplayName("${year} → NameRef（向后兼容旧 ${word} 语法）")
    void nameRefBackwardCompatible() {
        Value v = Templates.parse("${year}");
        assertInstanceOf(Value.NameRef.class, v);
        assertEquals("year", ((Value.NameRef) v).name());
    }

    // ============================================================
    // 字段引用
    // ============================================================

    @Test
    @DisplayName("${d.name} → FieldValue")
    void fieldRef() {
        Value v = Templates.parse("${d.name}");
        assertInstanceOf(Value.FieldValue.class, v);
        FieldRef ref = ((Value.FieldValue) v).ref();
        assertEquals("d", ref.datasetId());
        assertEquals("name", ref.field());
    }

    @Test
    @DisplayName("${employees.salary} → FieldValue")
    void fieldRefLongName() {
        Value v = Templates.parse("${employees.salary}");
        assertInstanceOf(Value.FieldValue.class, v);
        FieldRef ref = ((Value.FieldValue) v).ref();
        assertEquals("employees", ref.datasetId());
        assertEquals("salary", ref.field());
    }

    // ============================================================
    // 聚合函数
    // ============================================================

    @Test
    @DisplayName("${COUNT(d.name)} → Aggregate(COUNT)")
    void countAggregate() {
        Value v = Templates.parse("${COUNT(d.name)}");
        assertInstanceOf(Value.Aggregate.class, v);
        Value.Aggregate agg = (Value.Aggregate) v;
        assertEquals("COUNT", agg.aggregation());
        assertInstanceOf(Value.FieldValue.class, agg.operand());
        assertEquals("name", ((Value.FieldValue) agg.operand()).ref().field());
    }

    @Test
    @DisplayName("${SUM(d.salary)} → Aggregate(SUM)")
    void sumAggregate() {
        Value v = Templates.parse("${SUM(d.salary)}");
        assertInstanceOf(Value.Aggregate.class, v);
        assertEquals("SUM", ((Value.Aggregate) v).aggregation());
    }

    @Test
    @DisplayName("${AVG(d.score)} → Aggregate(AVG)")
    void avgAggregate() {
        Value v = Templates.parse("${AVG(d.score)}");
        assertInstanceOf(Value.Aggregate.class, v);
        assertEquals("AVG", ((Value.Aggregate) v).aggregation());
    }

    @Test
    @DisplayName("${MAX(d.price)} → Aggregate(MAX)")
    void maxAggregate() {
        Value v = Templates.parse("${MAX(d.price)}");
        assertInstanceOf(Value.Aggregate.class, v);
        assertEquals("MAX", ((Value.Aggregate) v).aggregation());
    }

    @Test
    @DisplayName("${MIN(d.price)} → Aggregate(MIN)")
    void minAggregate() {
        Value v = Templates.parse("${MIN(d.price)}");
        assertInstanceOf(Value.Aggregate.class, v);
        assertEquals("MIN", ((Value.Aggregate) v).aggregation());
    }

    @Test
    @DisplayName("${COUNT_DISTINCT(d.dept)} → Aggregate(COUNT_DISTINCT)")
    void countDistinctAggregate() {
        Value v = Templates.parse("${COUNT_DISTINCT(d.dept)}");
        assertInstanceOf(Value.Aggregate.class, v);
        assertEquals("COUNT_DISTINCT", ((Value.Aggregate) v).aggregation());
    }

    @Test
    @DisplayName("小写 count(d.name) 也能识别聚合")
    void lowercaseAggregate() {
        Value v = Templates.parse("${count(d.name)}");
        assertInstanceOf(Value.Aggregate.class, v);
        assertEquals("COUNT", ((Value.Aggregate) v).aggregation());
    }

    // ============================================================
    // 函数调用
    // ============================================================

    @Test
    @DisplayName("${format(d.name)} → FunctionCall")
    void functionCall() {
        Value v = Templates.parse("${format(d.name)}");
        assertInstanceOf(Value.FunctionCall.class, v);
        Value.FunctionCall fc = (Value.FunctionCall) v;
        assertEquals("format", fc.name());
        assertEquals(1, fc.args().size());
        assertInstanceOf(Value.FieldValue.class, fc.args().get(0));
    }

    @Test
    @DisplayName("${concat(d.first, d.last)} → FunctionCall 多参数")
    void functionCallMultiArgs() {
        Value v = Templates.parse("${concat(d.first, d.last)}");
        assertInstanceOf(Value.FunctionCall.class, v);
        Value.FunctionCall fc = (Value.FunctionCall) v;
        assertEquals("concat", fc.name());
        assertEquals(2, fc.args().size());
        assertInstanceOf(Value.FieldValue.class, fc.args().get(0));
        assertInstanceOf(Value.FieldValue.class, fc.args().get(1));
    }

    @Test
    @DisplayName("${date(d.createTime)} → FunctionCall（非聚合函数名）")
    void dateFunctionCall() {
        Value v = Templates.parse("${date(d.createTime)}");
        assertInstanceOf(Value.FunctionCall.class, v);
        assertEquals("date", ((Value.FunctionCall) v).name());
    }

    @Test
    @DisplayName("${if(d.flag, '是', '否')} → 三参数 FunctionCall")
    void ifFunctionCall() {
        Value v = Templates.parse("${if(d.flag, '是', '否')}");
        assertInstanceOf(Value.FunctionCall.class, v);
        Value.FunctionCall fc = (Value.FunctionCall) v;
        assertEquals("if", fc.name());
        assertEquals(3, fc.args().size());
        assertInstanceOf(Value.Literal.class, fc.args().get(1));
        assertEquals("是", ((Value.Literal) fc.args().get(1)).value());
    }

    @Test
    @DisplayName("${concat(f(a), \"x,y\")} → 嵌套括号与字符串字面量中的逗号不参与分割")
    void nestedArgsAndQuotedComma() {
        Value v = Templates.parse("${concat(round(d.salary, 2), \"x,y\")}");
        assertInstanceOf(Value.FunctionCall.class, v);
        Value.FunctionCall fc = (Value.FunctionCall) v;
        assertEquals("concat", fc.name());
        assertEquals(2, fc.args().size());
        // 第一参数是嵌套的 round(...) FunctionCall
        assertInstanceOf(Value.FunctionCall.class, fc.args().get(0));
        assertEquals("round", ((Value.FunctionCall) fc.args().get(0)).name());
        // 第二参数是含逗号的字符串字面量，不应被拆分
        assertInstanceOf(Value.Literal.class, fc.args().get(1));
        assertEquals("x,y", ((Value.Literal) fc.args().get(1)).value());
    }

    // ============================================================
    // 文本 + 表达式混合（Template）
    // ============================================================

    @Test
    @DisplayName("${name}的薪资 → Template([Hole(NameRef), Text])")
    void templateNameRefAndText() {
        Value v = Templates.parse("${name}的薪资");
        assertInstanceOf(Value.Template.class, v);
        Value.Template tpl = (Value.Template) v;
        assertEquals(2, tpl.parts().size());
        assertInstanceOf(Value.Template.Hole.class, tpl.parts().get(0));
        assertInstanceOf(Value.NameRef.class, ((Value.Template.Hole) tpl.parts().get(0)).value());
        assertInstanceOf(Value.Template.Text.class, tpl.parts().get(1));
        assertEquals("的薪资", ((Value.Template.Text) tpl.parts().get(1)).text());
    }

    @Test
    @DisplayName("部门人数为 ${COUNT(d.name)} → Template([Text, Hole(Aggregate)])")
    void templateTextAndAggregate() {
        Value v = Templates.parse("部门人数为 ${COUNT(d.name)}");
        assertInstanceOf(Value.Template.class, v);
        Value.Template tpl = (Value.Template) v;
        assertEquals(2, tpl.parts().size());

        assertEquals("部门人数为 ", ((Value.Template.Text) tpl.parts().get(0)).text());

        Value.Template.Hole hole = (Value.Template.Hole) tpl.parts().get(1);
        assertInstanceOf(Value.Aggregate.class, hole.value());
        assertEquals("COUNT", ((Value.Aggregate) hole.value()).aggregation());
    }

    @Test
    @DisplayName("合计 ${SUM(d.salary)} 元 → Template([Text, Hole(Aggregate), Text])")
    void templateTextAggregateText() {
        Value v = Templates.parse("合计 ${SUM(d.salary)} 元");
        assertInstanceOf(Value.Template.class, v);
        Value.Template tpl = (Value.Template) v;
        assertEquals(3, tpl.parts().size());
        assertEquals("合计 ", ((Value.Template.Text) tpl.parts().get(0)).text());
        assertInstanceOf(Value.Aggregate.class, ((Value.Template.Hole) tpl.parts().get(1)).value());
        assertEquals(" 元", ((Value.Template.Text) tpl.parts().get(2)).text());
    }

    @Test
    @DisplayName("${d.name}(${d.dept}) → Template 含多个 FieldValue 洞")
    void templateMultipleFieldHoles() {
        Value v = Templates.parse("${d.name}(${d.dept})");
        assertInstanceOf(Value.Template.class, v);
        Value.Template tpl = (Value.Template) v;
        assertEquals(4, tpl.parts().size());
        assertInstanceOf(
                Value.FieldValue.class, ((Value.Template.Hole) tpl.parts().get(0)).value());
        assertEquals("(", ((Value.Template.Text) tpl.parts().get(1)).text());
        assertInstanceOf(
                Value.FieldValue.class, ((Value.Template.Hole) tpl.parts().get(2)).value());
        assertEquals(")", ((Value.Template.Text) tpl.parts().get(3)).text());
    }

    @Test
    @DisplayName("${a}${b} → Template 相邻洞无间隔文本")
    void adjacentHoles() {
        Value v = Templates.parse("${a}${b}");
        assertInstanceOf(Value.Template.class, v);
        Value.Template tpl = (Value.Template) v;
        assertEquals(2, tpl.parts().size());
        assertEquals(
                "a", ((Value.NameRef) ((Value.Template.Hole) tpl.parts().get(0)).value()).name());
        assertEquals(
                "b", ((Value.NameRef) ((Value.Template.Hole) tpl.parts().get(1)).value()).name());
    }

    // ============================================================
    // containsAggregate
    // ============================================================

    @Test
    @DisplayName("containsAggregate：直接聚合 → true")
    void containsAggDirect() {
        Value v = Templates.parse("${COUNT(d.name)}");
        assertTrue(Templates.containsAggregate(v));
    }

    @Test
    @DisplayName("containsAggregate：Template 内嵌聚合 → true")
    void containsAggInTemplate() {
        Value v = Templates.parse("人数: ${COUNT(d.name)}");
        assertTrue(Templates.containsAggregate(v));
    }

    @Test
    @DisplayName("containsAggregate：纯字段 → false")
    void containsAggFieldOnly() {
        Value v = Templates.parse("${d.name}");
        assertFalse(Templates.containsAggregate(v));
    }

    @Test
    @DisplayName("containsAggregate：Literal → false")
    void containsAggLiteral() {
        Value v = Templates.parse("纯文本");
        assertFalse(Templates.containsAggregate(v));
    }

    @Test
    @DisplayName("containsAggregate：FunctionCall 无聚合 → false")
    void containsAggFuncNoAgg() {
        Value v = Templates.parse("${format(d.name)}");
        assertFalse(Templates.containsAggregate(v));
    }
}
