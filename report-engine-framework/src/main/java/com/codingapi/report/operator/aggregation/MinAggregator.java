package com.codingapi.report.operator.aggregation;

import java.util.stream.DoubleStream;

/** 最小值：{@link Aggregation#MIN}，无数值时为 0。 */
public class MinAggregator extends NumericAggregator {

    @Override
    public boolean supports(Aggregation aggregation) {
        return aggregation == Aggregation.MIN;
    }

    @Override
    protected Object reduce(DoubleStream values) {
        return values.min().orElse(0);
    }
}
