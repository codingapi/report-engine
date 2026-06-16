package com.codingapi.report.starter.repository;

import java.util.List;

/**
 * 示例报表注册表：提供预存示例报表的有序 id 列表。
 * <p>
 * starter 提供默认空实现（见自动配置）；使用方（如 example）可预存示例报表并提供有序 id，
 * 供 {@code GET /api/report/configs/examples} 按序返回。
 */
public interface ExampleReportRegistry {

    /** 预存示例报表的有序 id 列表。 */
    List<String> exampleReportIds();
}
