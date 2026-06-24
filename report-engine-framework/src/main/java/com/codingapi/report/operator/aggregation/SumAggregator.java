package com.codingapi.report.operator.aggregation;

import com.codingapi.report.expression.function.FunctionMeta;
import java.util.List;
import java.util.stream.DoubleStream;

/** 求和：{@code "SUM"}，无数值时为 0.0。 */
public class SumAggregator extends NumericAggregator {

    @Override
    public boolean supports(String aggregation) {
        return "SUM".equals(aggregation);
    }

    @Override
    public FunctionMeta meta() {
        return new FunctionMeta("SUM", "求和", List.of("数值字段"), "计算数值字段的总和，如 SUM(salary.amount)");
    }

    @Override
    protected Object reduce(DoubleStream values) {
        return values.sum();
    }
}
