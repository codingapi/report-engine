package com.codingapi.report.expression;

import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.operator.aggregation.Aggregators;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本插值语法糖：把用户输入的字符串编译成 {@link Value} 表达式树。
 *
 * <h3>支持的语法</h3>
 *
 * <ul>
 *   <li>{@code ${name}} — {@link Value.NameRef}（晚绑定，循环字段优先、再报表参数）
 *   <li>{@code ${d.name}} — {@link Value.FieldValue}（限定字段引用，d 为数据集 ID）
 *   <li>{@code ${COUNT(d.name)}} — {@link
 *       Value.Aggregate}（聚合函数：COUNT/SUM/AVG/MAX/MIN/COUNT_DISTINCT）
 *   <li>{@code ${format(d.name)}} — {@link Value.FunctionCall}（通用函数调用）
 *   <li>{@code 部门人数为 ${COUNT(d.name)}} — {@link Value.Template}（文本 + 表达式混合）
 * </ul>
 *
 * <h3>返回策略</h3>
 *
 * <ul>
 *   <li>纯文本（无 {@code ${}}）→ {@link Value.Literal}
 *   <li>整个字符串就是一个 {@code ${...}}（无前后文本）→ 直接返回洞内 Value（不套 Template）
 *   <li>文本 + 洞混合 → {@link Value.Template}
 * </ul>
 */
public final class Templates {

    private static final Pattern HOLE = Pattern.compile("\\$\\{([^}]+)}");

    private Templates() {}

    /** 把含 {@code ${...}} 占位的字符串解析为值表达式。 */
    public static Value parse(String template) {
        List<Value.Template.Part> parts = new ArrayList<>();
        Matcher m = HOLE.matcher(template);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                parts.add(new Value.Template.Text(template.substring(last, m.start())));
            }
            parts.add(new Value.Template.Hole(parseHole(m.group(1).trim())));
            last = m.end();
        }

        // 纯文本无洞
        if (parts.isEmpty()) {
            return new Value.Literal(template);
        }

        // 整个字符串就是一个 ${...}：不套 Template，直接返回洞内值
        if (parts.size() == 1 && last == template.length() && template.startsWith("${")) {
            return ((Value.Template.Hole) parts.get(0)).value();
        }

        if (last < template.length()) {
            parts.add(new Value.Template.Text(template.substring(last)));
        }
        return new Value.Template(parts);
    }

    // ============================================================
    // 洞内表达式解析
    // ============================================================

    /**
     * 解析 {@code ${...}} 内部的表达式文本。
     *
     * <p>按优先级识别：函数调用（含 {@code (}）→ 字段引用（含 {@code .}）→ 名字引用。
     */
    static Value parseHole(String content) {
        // 字符串字面量："..." 或 '...'（含逗号、括号等字符不参与解析）
        if (content.length() >= 2) {
            char q = content.charAt(0);
            if ((q == '"' || q == '\'') && content.charAt(content.length() - 1) == q) {
                return new Value.Literal(content.substring(1, content.length() - 1));
            }
        }
        int parenIdx = content.indexOf('(');
        if (parenIdx > 0 && content.endsWith(")")) {
            return parseCall(content, parenIdx);
        }

        int dotIdx = content.indexOf('.');
        if (dotIdx > 0) {
            return new Value.FieldValue(
                    new FieldRef(content.substring(0, dotIdx), content.substring(dotIdx + 1)));
        }

        return new Value.NameRef(content);
    }

    /** 解析函数/聚合调用：{@code NAME(arg1, arg2, ...)} */
    private static Value parseCall(String content, int parenIdx) {
        String name = content.substring(0, parenIdx).trim();
        String argsStr = content.substring(parenIdx + 1, content.length() - 1).trim();

        if (isAggregation(name)) {
            Value operand = argsStr.isEmpty() ? new Value.Literal(null) : parseHole(argsStr);
            return new Value.Aggregate(name.toUpperCase(), operand);
        }

        List<Value> args = argsStr.isEmpty() ? List.of() : splitArgs(argsStr);
        return new Value.FunctionCall(name, args);
    }

    /** 判断名字是否为聚合关键字（由 {@link Aggregators} 注册表分发） */
    private static boolean isAggregation(String name) {
        return Aggregators.isRegistered(name);
    }

    /**
     * 按顶层逗号分割参数并逐个解析。支持嵌套括号（{@code if(f(a), b, c)}）与 字符串字面量中的逗号（{@code concat("a,b",
     * x)}）——括号深度与引号内的逗号不参与分割。
     */
    private static List<Value> splitArgs(String argsStr) {
        List<Value> args = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < argsStr.length(); i++) {
            char c = argsStr.charAt(i);
            if (quote != 0) {
                cur.append(c);
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                cur.append(c);
            } else if (c == '(') {
                depth++;
                cur.append(c);
            } else if (c == ')') {
                depth--;
                cur.append(c);
            } else if (c == ',' && depth == 0) {
                args.add(parseHole(cur.toString().trim()));
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        String tail = cur.toString().trim();
        if (!tail.isEmpty() || !args.isEmpty()) {
            args.add(parseHole(tail));
        }
        return args;
    }

    // ============================================================
    // 工具方法
    // ============================================================

    /**
     * 递归检测表达式树中是否包含聚合节点。
     *
     * <p>用于 {@code ReportRenderer.evalSingle} 判断格子应走行集合上下文还是首行上下文。 聚合可能嵌套在 {@link Value.Template}
     * 的洞内或 {@link Value.FunctionCall} 的参数中。
     */
    public static boolean containsAggregate(Value v) {
        if (v instanceof Value.Aggregate) {
            return true;
        }
        if (v instanceof Value.Template t) {
            for (Value.Template.Part p : t.parts()) {
                if (p instanceof Value.Template.Hole h && containsAggregate(h.value())) {
                    return true;
                }
            }
        }
        if (v instanceof Value.FunctionCall fc) {
            for (Value arg : fc.args()) {
                if (containsAggregate(arg)) {
                    return true;
                }
            }
        }
        return false;
    }
}
