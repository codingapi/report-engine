package com.codingapi.report.operator.aggregation;

import com.codingapi.report.expression.function.FunctionMeta;

import java.util.List;
import java.util.stream.DoubleStream;

/** 最小值：{@code "MIN"}，无数值时为 0。 */
public class MinAggregator extends NumericAggregator {

    @Override
    public boolean supports(String aggregation) {
        return "MIN".equals(aggregation);
    }

    @Override
    public FunctionMeta meta() {
        return new FunctionMeta("MIN", "最小值", List.of("数值字段"), "获取字段的最小值，如 MIN(salary.amount)");
    }

    @Override
    protected Object reduce(DoubleStream values) {
        return values.min().orElse(0);
    }
}
