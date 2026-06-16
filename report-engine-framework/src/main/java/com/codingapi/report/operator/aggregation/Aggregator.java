package com.codingapi.report.operator.aggregation;

import com.codingapi.report.data.dataset.FieldRef;

import java.util.List;
import java.util.Map;

/**
 * 聚合算子（SPI）：一种 {@link Aggregation} 对应一个实现，<b>把一组行的某字段汇总成单个值</b>。
 *
 * <h3>设计动机：用策略替代 switch</h3>
 * <p>原本聚合逻辑集中在一个 {@code switch(aggregation)} 里——每加一种聚合就要改这个方法。
 * 改为按聚合方式拆成独立策略后，新增聚合（如 COUNT_TRUE/COUNT_FALSE）= 新增一个实现类并注册，
 * 不改动已有代码。这与数据提取层的 {@code DataExtractor} 是同一套扩展范式。
 *
 * @see Aggregation
 * @see Aggregators
 */
public interface Aggregator {

    /** 是否支持指定聚合方式。{@link Aggregators} 遍历注册表，用此方法选中实现。 */
    boolean supports(Aggregation aggregation);

    /**
     * 对一组行的某字段做聚合。
     *
     * @param rows  参与聚合的行（通常已过过滤或分组）
     * @param field 聚合目标字段
     * @return 聚合结果（Number 或 Long），无数据时按各算子约定返回 0 / null
     */
    Object aggregate(List<Map<String, Object>> rows, FieldRef field);
}
