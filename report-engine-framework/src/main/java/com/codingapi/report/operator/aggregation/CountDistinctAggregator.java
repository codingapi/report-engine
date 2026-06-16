package com.codingapi.report.operator.aggregation;

import com.codingapi.report.data.dataset.FieldRef;

import java.util.List;
import java.util.Map;

/** 去重计数：{@link Aggregation#COUNT_DISTINCT}，统计该字段不重复值的个数。 */
public class CountDistinctAggregator implements Aggregator {

    @Override
    public boolean supports(Aggregation aggregation) {
        return aggregation == Aggregation.COUNT_DISTINCT;
    }

    @Override
    public Object aggregate(List<Map<String, Object>> rows, FieldRef field) {
        String col = field.qualified();
        return rows.stream().map(r -> r.get(col)).distinct().count();
    }
}
