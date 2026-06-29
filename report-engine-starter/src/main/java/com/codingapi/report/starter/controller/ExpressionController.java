package com.codingapi.report.starter.controller;

import com.codingapi.report.expression.function.FunctionMeta;
import com.codingapi.report.expression.function.Functions;
import com.codingapi.report.operator.aggregation.Aggregators;
import com.codingapi.springboot.framework.dto.response.SingleResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表达式元数据 API：向前端表达式构建器提供"可用公式"清单（聚合 + 函数）。
 *
 * <p>聚合由 {@link Aggregators} 注册表自描述（每个 {@code Aggregator} 提供 {@link FunctionMeta}）； 函数由 {@link
 * Functions} 注册表自描述。两边都是策略+注册表范式，新增聚合/函数只需登记一行。
 */
@RestController
@RequestMapping("/api/expression")
@ConditionalOnClass(RestController.class)
public class ExpressionController {

    @GetMapping("/functions")
    public SingleResponse<ExpressionCatalog> functions() {
        // NONE 是内部"不聚合"占位，不暴露给前端选择
        List<FunctionMeta> aggregations =
                Aggregators.list().stream().filter(m -> !"NONE".equals(m.name())).toList();
        // map 在前端「数据转换」独立分类提供，不在通用函数列表重复展示
        List<FunctionMeta> functions =
                Functions.list().stream().filter(m -> !"map".equals(m.name())).toList();
        return SingleResponse.of(new ExpressionCatalog(aggregations, functions));
    }

    /**
     * 表达式公式目录。
     *
     * @param aggregations 可用聚合函数（含元信息）
     * @param functions 可用通用函数（含元信息）
     */
    public record ExpressionCatalog(
            List<FunctionMeta> aggregations, List<FunctionMeta> functions) {}
}
