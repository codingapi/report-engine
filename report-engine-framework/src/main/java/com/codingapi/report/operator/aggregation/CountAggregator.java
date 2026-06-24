package com.codingapi.report.operator.aggregation;

import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.expression.function.FunctionMeta;
import java.util.List;
import java.util.Map;

/** 计数：{@code "COUNT"}，统计行数（含字段为 null 的行）。 */
public class CountAggregator implements Aggregator {

    @Override
    public boolean supports(String aggregation) {
        return "COUNT".equals(aggregation);
    }

    @Override
    public FunctionMeta meta() {
        return new FunctionMeta("COUNT", "计数", List.of("字段"), "统计行数，如 COUNT(employees.id)");
    }

    @Override
    public Object aggregate(List<Map<String, Object>> rows, FieldRef field) {
        return (long) rows.size();
    }
}
