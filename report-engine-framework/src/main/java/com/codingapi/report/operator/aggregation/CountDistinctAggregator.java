package com.codingapi.report.operator.aggregation;

import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.expression.function.FunctionMeta;

import java.util.List;
import java.util.Map;

/** 去重计数：{@code "COUNT_DISTINCT"}，统计该字段不重复值的个数。 */
public class CountDistinctAggregator implements Aggregator {

    @Override
    public boolean supports(String aggregation) {
        return "COUNT_DISTINCT".equals(aggregation);
    }

    @Override
    public FunctionMeta meta() {
        return new FunctionMeta("COUNT_DISTINCT", "去重计数", List.of("字段"), "统计不重复的行数，如 COUNT_DISTINCT(employees.dept)");
    }

    @Override
    public Object aggregate(List<Map<String, Object>> rows, FieldRef field) {
        String col = field.qualified();
        return rows.stream().map(r -> r.get(col)).distinct().count();
    }
}
