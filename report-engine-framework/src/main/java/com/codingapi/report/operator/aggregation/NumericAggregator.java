package com.codingapi.report.operator.aggregation;

import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.operator.Values;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.DoubleStream;

/**
 * 数值聚合算子基类：为 SUM/AVG/MAX/MIN 提供"取列 → 转 double → 跳过非数值/空值"的公共逻辑。 子类只需在 {@link #reduce} 里实现各自的归约方式。
 */
abstract class NumericAggregator implements Aggregator {

    @Override
    public Object aggregate(List<Map<String, Object>> rows, FieldRef field) {
        String col = field.qualified();
        DoubleStream nums =
                rows.stream()
                        .map(r -> Values.toDouble(r.get(col)))
                        .filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue);
        return reduce(nums);
    }

    /** 把一列数值归约为聚合结果（无数据时各算子自行约定默认值）。 */
    protected abstract Object reduce(DoubleStream values);
}
