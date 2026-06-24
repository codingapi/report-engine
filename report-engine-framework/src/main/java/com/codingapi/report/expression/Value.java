package com.codingapi.report.expression;

import com.codingapi.report.data.dataset.FieldRef;
import java.util.List;

/**
 * 值表达式树（密封接口）：统一描述"一个单元格/条件/汇总位置上的值<b>怎么算出来</b>"。
 *
 * <h3>定位：全模型统一的取值机制</h3>
 *
 * <p>报表里所有"动态值"——单元格内容、过滤条件的左右值、小计行的标签/聚合——本质都是"对运行时上下文 求一个值"。本接口把这件事收成一棵可 JSON 序列化的表达式树，由 {@link
 * ExpressionEngine} 求值。 这取代了原先散落的多套机制（{@code TextCell.template} 的 ${} 正则、{@code
 * FieldCell.aggregation}、 {@code ValueRef} 等）。
 *
 * <h3>节点类型（预先约定，密封穷尽）</h3>
 *
 * <ul>
 *   <li>{@link Literal} — 字面量/纯文本
 *   <li>{@link FieldValue} — 读当前行的某字段
 *   <li>{@link ParamValue} — 读报表参数
 *   <li>{@link LoopFieldValue} — 读循环当前迭代行的字段
 *   <li>{@link Template} — 文本插值（文本片段 + 表达式洞）
 *   <li>{@link Aggregate} — 对行集合按聚合名（如 SUM/COUNT）聚合一个子表达式
 *   <li>{@link FunctionCall} — 调用函数（格式化/日期/拼接/round…），函数本身可扩展注册
 * </ul>
 *
 * <h3>求值是策略机制</h3>
 *
 * <p>每种节点对应一个 {@link ValueEvaluator} 策略，由 {@link ExpressionEngine} 按 {@code supports()} 选中分发——与
 * {@code DataExtractor}/{@code Aggregator}/{@code ConditionPredicate} 同一套范式。
 */
public sealed interface Value
        permits Value.Literal,
                Value.FieldValue,
                Value.ParamValue,
                Value.LoopFieldValue,
                Value.NameRef,
                Value.Template,
                Value.Aggregate,
                Value.FunctionCall {

    /** 字面量/纯文本：固定值，直接返回。 */
    record Literal(Object value) implements Value {}

    /**
     * 晚绑定名字引用：按名字在运行时作用域里查找——<b>循环字段优先，再退到报表参数</b>。
     *
     * <p>文本插值 {@code ${name}} 编译成它：作者写名字时不必区分"这是参数还是循环字段"， 由 {@code ParamContext.lookup}
     * 按作用域就近解析。明确来源时用 {@link ParamValue}/{@link LoopFieldValue}。
     */
    record NameRef(String name) implements Value {}

    /** 字段读取：从求值上下文的当前行取 {@code ref} 对应的列值。 */
    record FieldValue(FieldRef ref) implements Value {}

    /** 报表参数引用：按名字从 {@code ParamContext} 的报表级参数取值。 */
    record ParamValue(String name) implements Value {}

    /** 循环字段引用：取指定循环块当前迭代行的字段值。 */
    record LoopFieldValue(String loopBlockId, String field) implements Value {}

    /**
     * 文本插值：由若干片段拼接而成，片段要么是静态文本，要么是一个表达式洞。
     *
     * <p>例：{@code "${year}年度报表"} 编译为 [Hole(ParamValue year), Text("年度报表")]。
     */
    record Template(List<Part> parts) implements Value {

        /** 模板片段（密封）：静态文本 {@link Text} 或表达式洞 {@link Hole}。 */
        public sealed interface Part permits Text, Hole {}

        /** 静态文本片段。 */
        public record Text(String text) implements Part {}

        /** 表达式洞：求值后格式化为字符串嵌入。 */
        public record Hole(Value value) implements Part {}
    }

    /**
     * 聚合：对上下文的行集合，按聚合名（如 {@code "SUM"} / {@code "COUNT"}）聚合 {@code operand} 求出的每行值。
     *
     * <p>聚合名由 {@link com.codingapi.report.operator.aggregation.Aggregators} 注册表分发到具体 {@code
     * Aggregator}。
     */
    record Aggregate(String aggregation, Value operand) implements Value {}

    /** 函数调用：参数先各自求值，再交给同名 {@code ValueFunction} 处理（格式化/日期等）。 */
    record FunctionCall(String name, List<Value> args) implements Value {}
}
