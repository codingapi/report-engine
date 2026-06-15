package com.codingapi.report.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本插值语法糖：把 {@code "${name}年度报表"} 这种字符串编译成 {@link Value.Template}。
 *
 * <p>{@code ${name}} 编译为 {@link Value.NameRef}（晚绑定，循环字段优先、再报表参数），
 * 其余文本编译为 {@link Value.Template.Text}。这是录入侧的便利写法，运行期统一走表达式引擎求值。
 */
public final class Templates {

    private static final Pattern HOLE = Pattern.compile("\\$\\{(\\w+)}");

    private Templates() {
    }

    /** 把含 {@code ${name}} 占位的字符串解析为模板表达式。 */
    public static Value parse(String template) {
        List<Value.Template.Part> parts = new ArrayList<>();
        Matcher m = HOLE.matcher(template);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                parts.add(new Value.Template.Text(template.substring(last, m.start())));
            }
            parts.add(new Value.Template.Hole(new Value.NameRef(m.group(1))));
            last = m.end();
        }
        if (last < template.length()) {
            parts.add(new Value.Template.Text(template.substring(last)));
        }
        return new Value.Template(parts);
    }
}
