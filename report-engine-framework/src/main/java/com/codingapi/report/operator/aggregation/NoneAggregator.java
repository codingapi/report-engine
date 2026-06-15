package com.codingapi.report.operator.aggregation;

import com.codingapi.report.data.dataset.FieldRef;

import java.util.List;
import java.util.Map;

/** 不聚合：{@link Aggregation#NONE}，返回 null（明细模式取原值的占位，不走聚合）。 */
public class NoneAggregator implements Aggregator {

    @Override
    public boolean supports(Aggregation aggregation) {
        return aggregation == Aggregation.NONE;
    }

    @Override
    public Object aggregate(List<Map<String, Object>> rows, FieldRef field) {
        return null;
    }
}
