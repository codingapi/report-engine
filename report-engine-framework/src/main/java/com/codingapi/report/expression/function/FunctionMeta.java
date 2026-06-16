package com.codingapi.report.expression.function;

import java.util.List;

/**
 * 函数元信息：供前端表达式构建器展示与选择。
 *
 * @param name        函数名（表达式中使用，如 {@code format}）
 * @param label       显示名（如"数值格式化"）
 * @param params      参数说明列表（按顺序，如 ["数值", "格式模式"]）
 * @param description 用法说明
 */
public record FunctionMeta(String name, String label, List<String> params, String description) {
}
