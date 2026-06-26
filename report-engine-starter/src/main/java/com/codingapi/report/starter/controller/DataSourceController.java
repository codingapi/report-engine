package com.codingapi.report.starter.controller;

import com.codingapi.report.config.dto.DataModelDtos.DataSourceDTO;
import com.codingapi.report.data.datasource.ColumnMeta;
import com.codingapi.report.data.datasource.TestResult;
import com.codingapi.report.starter.service.DataSourceService;
import com.codingapi.springboot.framework.dto.response.MultiResponse;
import com.codingapi.springboot.framework.dto.response.SingleResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据源（连接）操作 API：连接测试 + 表/列探查。
 *
 * <p>仅做 HTTP 编排，业务（提取器派发、连接解密加载、探查）下沉 {@link DataSourceService}。
 */
@RestController
@RequestMapping("/api/datasources")
@ConditionalOnClass(RestController.class)
public class DataSourceController {

    private final DataSourceService dataSourceService;

    public DataSourceController(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    @PostMapping("/test")
    public SingleResponse<TestResult> test(@RequestBody DataSourceDTO dto) {
        return SingleResponse.of(dataSourceService.testConnection(dto));
    }

    @GetMapping("/{dataModelId}/{datasourceId}/tables")
    public MultiResponse<String> tables(
            @PathVariable String dataModelId, @PathVariable String datasourceId) {
        return MultiResponse.of(dataSourceService.listTables(dataModelId, datasourceId));
    }

    @GetMapping("/{dataModelId}/{datasourceId}/columns")
    public MultiResponse<ColumnMeta> columns(
            @PathVariable String dataModelId,
            @PathVariable String datasourceId,
            @RequestParam String table) {
        return MultiResponse.of(dataSourceService.listColumns(dataModelId, datasourceId, table));
    }
}
