package com.codingapi.report.operator.aggregation;

import java.util.stream.DoubleStream;

/** 最大值：{@link Aggregation#MAX}，无数值时为 0。 */
public class MaxAggregator extends NumericAggregator {

    @Override
    public boolean supports(Aggregation aggregation) {
        return aggregation == Aggregation.MAX;
    }

    @Override
    protected Object reduce(DoubleStream values) {
        return values.max().orElse(0);
    }
}
