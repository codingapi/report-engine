package com.codingapi.report.operator.aggregation;

import java.util.stream.DoubleStream;

/** 平均值：{@link Aggregation#AVG}，无数值时为 0.0。 */
public class AvgAggregator extends NumericAggregator {

    @Override
    public boolean supports(Aggregation aggregation) {
        return aggregation == Aggregation.AVG;
    }

    @Override
    protected Object reduce(DoubleStream values) {
        return values.average().orElse(0.0);
    }
}
