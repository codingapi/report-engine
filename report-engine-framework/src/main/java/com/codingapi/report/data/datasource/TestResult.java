package com.codingapi.report.data.datasource;

/**
 * 连接测试结果：{@link DataExtractor#test(DataSource)} 的返回值。
 *
 * <p>{@code ok=true} 表示连接可达且凭证有效；{@code ok=false} 时 {@code message} 携带失败原因
 * （驱动缺失、认证失败、网络不通等），供前端"测试连接"按钮直接展示。
 *
 * @param ok 是否连通
 * @param message 结果说明（成功为提示语，失败为原因）
 * @param latencyMs 建连 + 探测耗时（毫秒），用于前端展示连接速度
 */
public record TestResult(boolean ok, String message, long latencyMs) {}
