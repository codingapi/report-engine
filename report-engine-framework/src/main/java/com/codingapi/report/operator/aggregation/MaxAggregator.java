package com.codingapi.report.operator.aggregation;

import com.codingapi.report.expression.function.FunctionMeta;
import java.util.List;
import java.util.stream.DoubleStream;

/** 最大值：{@code "MAX"}，无数值时为 0。 */
public class MaxAggregator extends NumericAggregator {

    @Override
    public boolean supports(String aggregation) {
        return "MAX".equals(aggregation);
    }

    @Override
    public FunctionMeta meta() {
        return new FunctionMeta("MAX", "最大值", List.of("数值字段"), "获取字段的最大值，如 MAX(salary.amount)");
    }

    @Override
    protected Object reduce(DoubleStream values) {
        return values.max().orElse(0);
    }
}
