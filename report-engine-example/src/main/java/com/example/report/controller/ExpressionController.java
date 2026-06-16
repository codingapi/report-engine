package com.example.report.controller;

import com.codingapi.report.expression.function.FunctionMeta;
import com.codingapi.report.expression.function.Functions;
import com.codingapi.report.operator.aggregation.Aggregation;
import com.codingapi.springboot.framework.dto.response.SingleResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 表达式元数据 API：向前端表达式构建器提供"可用公式"清单（聚合 + 函数）。
 * <p>
 * 聚合来自 {@link Aggregation}（固定枚举，排除 NONE）；
 * 函数来自 {@link Functions} 注册表（可扩展，含名称/参数/说明）。
 * </p>
 */
@RestController
@RequestMapping("/api/expression")
public class ExpressionController {

    @GetMapping("/functions")
    public SingleResponse<ExpressionCatalog> functions() {
        List<FunctionMeta> aggregations = Arrays.stream(Aggregation.values())
                .filter(a -> a != Aggregation.NONE)
                .map(ExpressionController::toAggregationMeta)
                .toList();
        return SingleResponse.of(new ExpressionCatalog(aggregations, Functions.list()));
    }

    /** 为聚合函数生成元信息 */
    private static FunctionMeta toAggregationMeta(Aggregation agg) {
        String name = agg.name();
        String label;
        String description;
        switch (agg) {
            case COUNT -> { label = "计数"; description = "统计行数，如 COUNT(employees.id)"; }
            case COUNT_DISTINCT -> { label = "去重计数"; description = "统计不重复的行数，如 COUNT_DISTINCT(employees.dept)"; }
            case SUM -> { label = "求和"; description = "计算数值字段的总和，如 SUM(salary.amount)"; }
            case AVG -> { label = "平均值"; description = "计算数值字段的平均值，如 AVG(salary.amount)"; }
            case MAX -> { label = "最大值"; description = "获取字段的最大值，如 MAX(salary.amount)"; }
            case MIN -> { label = "最小值"; description = "获取字段的最小值，如 MIN(salary.amount)"; }
            default -> { label = name; description = ""; }
        }
        return new FunctionMeta(name, label, List.of("字段"), description);
    }

    /**
     * 表达式公式目录。
     *
     * @param aggregations 可用聚合函数（含元信息）
     * @param functions    可用通用函数（含元信息）
     */
    public record ExpressionCatalog(List<FunctionMeta> aggregations, List<FunctionMeta> functions) {
    }
}
