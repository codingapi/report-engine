package com.codingapi.report.operator.aggregation;

import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.expression.function.FunctionMeta;

import java.util.List;
import java.util.Map;

/** 不聚合：{@code "NONE"}，返回 null（明细模式取原值的占位，不走聚合）。 */
public class NoneAggregator implements Aggregator {

    @Override
    public boolean supports(String aggregation) {
        return "NONE".equals(aggregation);
    }

    @Override
    public FunctionMeta meta() {
        return new FunctionMeta("NONE", "不聚合", List.of("字段"), "取原值，不做汇总（明细模式下每行各自的值）");
    }

    @Override
    public Object aggregate(List<Map<String, Object>> rows, FieldRef field) {
        return null;
    }
}
