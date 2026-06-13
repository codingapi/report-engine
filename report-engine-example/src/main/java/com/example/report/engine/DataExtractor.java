package com.example.report.engine;

import com.example.report.model.source.DataSource;
import com.example.report.model.source.DataSourceType;
import com.example.report.model.source.Dataset;

/**
 * 提取器：每种数据源一个实现，<b>唯一职责是把数据取成规整的 {@link RawTable}</b>。
 *
 * <p>这是"提取 / 加工"边界——提取在源侧，之后的 filter/join/aggregate 全在 Java。
 * 最小实现先取整表；性能版可在此做单源本地过滤下推 + 投影。
 */
public interface DataExtractor {

    boolean supports(DataSourceType type);

    RawTable extract(DataSource source, Dataset dataset);
}
