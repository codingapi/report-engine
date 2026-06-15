package com.codingapi.report.expression.function;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 日期格式化函数 {@code date(value, pattern)}：把 ISO 日期/日期时间字符串按给定模式重新格式化。
 * <p>例：{@code date("2026-06-15", "yyyy/MM/dd")} → {@code "2026/06/15"}。
 * <p>当前最小实现：接受 ISO_LOCAL_DATE 或 ISO_LOCAL_DATE_TIME 字符串；无法解析则原样返回。
 */
public class DateFunction implements ValueFunction {

    @Override
    public boolean supports(String name) {
        return "date".equals(name);
    }

    @Override
    public Object apply(List<Object> args) {
        Object value = args.get(0);
        if (value == null) {
            return "";
        }
        String pattern = String.valueOf(args.get(1));
        String text = String.valueOf(value);
        DateTimeFormatter out = DateTimeFormatter.ofPattern(pattern);
        try {
            return LocalDateTime.parse(text).format(out);
        } catch (Exception ignore) {
            // 退化为按纯日期解析
        }
        try {
            return LocalDate.parse(text).format(out);
        } catch (Exception ignore) {
            return text;
        }
    }
}
