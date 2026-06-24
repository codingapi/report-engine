package com.codingapi.report.operator.aggregation;

import com.codingapi.report.expression.function.FunctionMeta;
import java.util.List;
import java.util.stream.DoubleStream;

/** 平均值：{@code "AVG"}，无数值时为 0.0。 */
public class AvgAggregator extends NumericAggregator {

    @Override
    public boolean supports(String aggregation) {
        return "AVG".equals(aggregation);
    }

    @Override
    public FunctionMeta meta() {
        return new FunctionMeta("AVG", "平均值", List.of("数值字段"), "计算数值字段的平均值，如 AVG(salary.amount)");
    }

    @Override
    protected Object reduce(DoubleStream values) {
        return values.average().orElse(0.0);
    }
}
