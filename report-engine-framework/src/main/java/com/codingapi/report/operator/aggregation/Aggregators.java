package com.codingapi.report.operator.aggregation;

import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.expression.function.FunctionMeta;

import java.util.List;
import java.util.Map;

/**
 * 聚合算子注册表：内置全部 {@link Aggregator} 实现，按聚合名选中并求值。
 *
 * <h3>扩展方式</h3>
 * <p>新增一种聚合（如 BOOLEAN 类型的 COUNT_TRUE/COUNT_FALSE）：实现 {@link Aggregator}，
 * 在 {@link #REGISTRY} 列表里登记一行即可，无需改动调用方。未注册的聚合名会显式抛异常。
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

    /** 列出所有已注册聚合的元信息（供前端表达式构建器枚举）。 */
    public static List<FunctionMeta> list() {
        return REGISTRY.stream().map(Aggregator::meta).toList();
    }

    /** 是否已注册该聚合名（大小写不敏感，供表达式解析器区分聚合 vs 函数调用）。 */
    public static boolean isRegistered(String name) {
        if (name == null) return false;
        String upper = name.toUpperCase();
        for (Aggregator a : REGISTRY) {
            if (a.supports(upper)) return true;
        }
        return false;
    }

    /**
     * 对一组行的某字段按指定方式聚合。
     *
     * @throws IllegalStateException 当没有策略支持该聚合名
     */
    public static Object aggregate(String aggregation, List<Map<String, Object>> rows, FieldRef field) {
        for (Aggregator a : REGISTRY) {
            if (a.supports(aggregation)) {
                return a.aggregate(rows, field);
            }
        }
        throw new IllegalStateException("不支持的聚合方式: " + aggregation);
    }
}
