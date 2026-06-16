package com.codingapi.report.operator.aggregation;

import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.expression.function.FunctionMeta;

import java.util.List;
import java.util.Map;

/**
 * 聚合算子（SPI）：一个具名聚合对应一个实现，<b>把一组行的某字段汇总成单个值</b>。
 *
 * <h3>设计动机：用策略替代 switch</h3>
 * <p>原本聚合逻辑集中在一个 {@code switch(aggregation)} 里——每加一种聚合就要改这个方法。
 * 改为按聚合方式拆成独立策略后，新增聚合（如 COUNT_TRUE/COUNT_FALSE）= 新增一个实现类并注册，
 * 不改动已有代码。这与 {@code DataExtractor} / {@code ValueFunction} 是同一套扩展范式：
 * {@code supports(name)} 选中 + {@link Aggregators} 注册表分发。
 *
 * <h3>元信息（借鉴 {@link com.codingapi.report.expression.function.ValueFunction}）</h3>
 * <p>每个算子通过 {@link #meta()} 自描述（名称/显示名/参数/说明），供前端表达式构建器枚举与展示，
 * 前端不再硬编码聚合清单，由注册表统一分发。
 *
 * @see Aggregators
 * @see com.codingapi.report.expression.function.ValueFunction
 */
public interface Aggregator {

    /** 是否支持指定聚合名（大小写敏感，注册时使用大写如 {@code "SUM"}）。 */
    boolean supports(String aggregation);

    /** 聚合元信息（名称/显示名/参数/说明），供前端表达式构建器枚举与展示。 */
    FunctionMeta meta();

    /**
     * 对一组行的某字段做聚合。
     *
     * @param rows  参与聚合的行（通常已过过滤或分组）
     * @param field 聚合目标字段
     * @return 聚合结果（Number 或 Long），无数据时按各算子约定返回 0 / null
     */
    Object aggregate(List<Map<String, Object>> rows, FieldRef field);
}
