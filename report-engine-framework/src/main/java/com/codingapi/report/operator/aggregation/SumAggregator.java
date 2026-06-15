package com.codingapi.report.operator.aggregation;

import java.util.stream.DoubleStream;

/** 求和：{@link Aggregation#SUM}，无数值时为 0.0。 */
public class SumAggregator extends NumericAggregator {

    @Override
    public boolean supports(Aggregation aggregation) {
        return aggregation == Aggregation.SUM;
    }

    @Override
    protected Object reduce(DoubleStream values) {
        return values.sum();
    }
}
