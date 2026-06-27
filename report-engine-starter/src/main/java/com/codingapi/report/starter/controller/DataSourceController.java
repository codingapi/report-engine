package com.codingapi.report.starter.controller;

import com.codingapi.report.data.datasource.ColumnMeta;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.IntrospectedTable;
import com.codingapi.report.data.datasource.TestResult;
import com.codingapi.report.dto.datamodel.DataSourceDTO;
import com.codingapi.report.repository.PageResult;
import com.codingapi.report.starter.service.DataSourceService;
import com.codingapi.report.starter.service.DataSourceService.UploadResult;
import com.codingapi.springboot.framework.dto.response.MultiResponse;
import com.codingapi.springboot.framework.dto.response.SingleResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据源（连接）操作 API：CRUD + 连接测试 + 表/列探查。
 *
 * <p>仅做 HTTP 编排，业务（CRUD + 凭证脱敏/回填、提取器派发、连接解密加载、探查、外部驱动注册）下沉 {@link DataSourceService}。
 */
@RestController
@RequestMapping("/api/datasources")
@ConditionalOnClass(RestController.class)
public class DataSourceController {

    private final DataSourceService dataSourceService;

    public DataSourceController(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    @GetMapping
    public MultiResponse<DataSourceBrief> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResult<DataSource> result = dataSourceService.page(current, pageSize);
        List<DataSourceBrief> briefs =
                result.content().stream()
                        .map(
                                ds ->
                                        new DataSourceBrief(
                                                ds.getId(),
                                                ds.getName() != null ? ds.getName() : "未命名连接",
                                                ds.getType() != null ? ds.getType().type() : null,
                                                ds.getTypeConfigId(),
                                                ds.getDatasets() != null
                                                        ? ds.getDatasets().size()
                                                        : 0,
                                                ds.getCreateTime(),
                                                ds.getUpdateTime()))
                        .toList();
        return MultiResponse.of(briefs, result.total());
    }

    @GetMapping("/{id}")
    public SingleResponse<DataSourceDTO> get(@PathVariable String id) {
        return SingleResponse.of(dataSourceService.getMasked(id));
    }

    @PostMapping
    public SingleResponse<String> save(@RequestBody DataSourceDTO dto) {
        return SingleResponse.of(dataSourceService.save(dto));
    }

    @PostMapping("/{id}/delete")
    public SingleResponse<Void> delete(@PathVariable String id) {
        dataSourceService.delete(id);
        return SingleResponse.of(null);
    }

    @PostMapping("/test")
    public SingleResponse<TestResult> test(@RequestBody DataSourceDTO dto) {
        return SingleResponse.of(dataSourceService.testConnection(dto));
    }

    /** 元数据探查：按已保存的连接 id 解析所有表/sheet + 列定义。 DB 返回物理表列表；EXCEL 每个 sheet 一张表；CSV 单张表。 */
    @PostMapping("/{id}/introspect")
    public MultiResponse<IntrospectedTable> introspect(@PathVariable String id) {
        return MultiResponse.of(dataSourceService.introspect(id));
    }

    /**
     * 元数据探查：按 DTO 配置直接解析（不落库），供数据源向导「解析」使用。
     *
     * <p>与 {@code /{id}/introspect} 的区别：不读仓库、不落库、不合并已保存元数据；只有最终「保存」才落库，避免解析阶段产生半成品数据源。
     */
    @PostMapping("/introspect")
    public MultiResponse<IntrospectedTable> introspectByConfig(@RequestBody DataSourceDTO dto) {
        return MultiResponse.of(dataSourceService.introspect(dto));
    }

    /**
     * 上传 Excel/CSV 文件到配置目录并返回解析出的表/列元数据。 EXCEL 落到 {@code codingapi.report.excel.dir}，CSV 落到 {@code
     * codingapi.report.csv.dir}。
     *
     * @param file 上传文件
     * @param type {@code EXCEL} 或 {@code CSV}；省略时按扩展名推断
     */
    @PostMapping("/upload")
    public SingleResponse<UploadResult> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", required = false) String type)
            throws IOException {
        return SingleResponse.of(dataSourceService.uploadAndIntrospect(file, type));
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

    /** 列表项：不含 {@code config}（敏感字段不暴露到列表）。 */
    public record DataSourceBrief(
            String id,
            String name,
            String type,
            String typeConfigId,
            int datasetCount,
            long createTime,
            long updateTime) {}
}
