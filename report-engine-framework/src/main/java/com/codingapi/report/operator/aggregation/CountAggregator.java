package com.codingapi.report.operator.aggregation;

import com.codingapi.report.data.dataset.FieldRef;

import java.util.List;
import java.util.Map;

/** 计数：{@link Aggregation#COUNT}，统计行数（含字段为 null 的行）。 */
public class CountAggregator implements Aggregator {

    @Override
    public boolean supports(Aggregation aggregation) {
        return aggregation == Aggregation.COUNT;
    }

    @Override
    public Object aggregate(List<Map<String, Object>> rows, FieldRef field) {
        return (long) rows.size();
    }
}
