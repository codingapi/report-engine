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
        List<String> aggregations = Arrays.stream(Aggregation.values())
                .filter(a -> a != Aggregation.NONE)
                .map(Enum::name)
                .toList();
        return SingleResponse.of(new ExpressionCatalog(aggregations, Functions.list()));
    }

    /**
     * 表达式公式目录。
     *
     * @param aggregations 可用聚合方式（枚举名，如 SUM/COUNT）
     * @param functions    可用函数（含元信息）
     */
    public record ExpressionCatalog(List<String> aggregations, List<FunctionMeta> functions) {
    }
}
