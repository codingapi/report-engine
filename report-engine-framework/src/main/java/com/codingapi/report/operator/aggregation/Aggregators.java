package com.codingapi.report.operator.aggregation;

import com.codingapi.report.data.dataset.FieldRef;

import java.util.List;
import java.util.Map;

/**
 * 聚合算子注册表：内置全部 {@link Aggregator} 实现，按 {@link Aggregation} 选中并求值。
 *
 * <h3>扩展方式</h3>
 * <p>新增一种聚合（如 BOOLEAN 类型的 COUNT_TRUE/COUNT_FALSE）：实现 {@link Aggregator}，
 * 在 {@link #REGISTRY} 列表里登记一行即可，无需改动 {@code Operators} 或任何调用方。
 */
public final class Aggregators {

    /** 内置聚合实现。新增聚合在此登记。 */
    private static final List<Aggregator> REGISTRY = List.of(
            new NoneAggregator(),
            new CountAggregator(),
            new CountDistinctAggregator(),
            new SumAggregator(),
            new AvgAggregator(),
            new MaxAggregator(),
            new MinAggregator()
    );

    private Aggregators() {
    }

    /**
     * 对一组行的某字段按指定方式聚合。
     *
     * @throws IllegalStateException 当没有策略支持该聚合方式
     */
    public static Object aggregate(Aggregation aggregation, List<Map<String, Object>> rows, FieldRef field) {
        for (Aggregator a : REGISTRY) {
            if (a.supports(aggregation)) {
                return a.aggregate(rows, field);
            }
        }
        throw new IllegalStateException("不支持的聚合方式: " + aggregation);
    }
}
