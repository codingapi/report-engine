package com.codingapi.report.starter.controller;

import com.codingapi.report.data.datasource.DataSourceTypeConfig;
import com.codingapi.report.dto.datasource.DataSourceTypeDTO;
import com.codingapi.report.repository.PageResult;
import com.codingapi.report.starter.service.DataSourceTypeService;
import com.codingapi.report.starter.service.DriverJarScanner;
import com.codingapi.springboot.framework.dto.response.MultiResponse;
import com.codingapi.springboot.framework.dto.response.SingleResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/datasource-types")
@ConditionalOnClass(RestController.class)
public class DataSourceTypeController {

    private final DataSourceTypeService dataSourceTypeService;

    public DataSourceTypeController(DataSourceTypeService dataSourceTypeService) {
        this.dataSourceTypeService = dataSourceTypeService;
    }

    @GetMapping
    public MultiResponse<DataSourceTypeBrief> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResult<DataSourceTypeConfig> result = dataSourceTypeService.page(current, pageSize);
        List<DataSourceTypeBrief> briefs =
                result.content().stream()
                        .map(
                                c ->
                                        new DataSourceTypeBrief(
                                                c.getId(),
                                                c.getName() != null ? c.getName() : "未命名类型",
                                                c.getType() != null ? c.getType().type() : null,
                                                c.getCreateTime(),
                                                c.getUpdateTime()))
                        .toList();
        return MultiResponse.of(briefs, result.total());
    }

    @GetMapping("/{id}")
    public SingleResponse<DataSourceTypeDTO> get(@PathVariable String id) {
        return SingleResponse.of(dataSourceTypeService.get(id));
    }

    @PostMapping
    public SingleResponse<String> save(@RequestBody DataSourceTypeDTO dto) {
        return SingleResponse.of(dataSourceTypeService.save(dto));
    }

    @DeleteMapping("/{id}")
    public SingleResponse<Void> delete(@PathVariable String id) {
        dataSourceTypeService.delete(id);
        return SingleResponse.of(null);
    }

    /** 上传驱动 jar，返回保存的文件名与扫描出的驱动类名列表供前端选择。 */
    @PostMapping("/driver-jar")
    public SingleResponse<DriverJarUploadResponse> uploadDriverJar(
            @RequestParam("file") MultipartFile file) throws IOException {
        DriverJarScanner.DriverJarScanResult result = dataSourceTypeService.uploadDriverJar(file);
        return SingleResponse.of(
                new DriverJarUploadResponse(result.jarFile(), result.driverClasses()));
    }

    public record DataSourceTypeBrief(
            String id, String name, String kind, long createTime, long updateTime) {}

    public record DriverJarUploadResponse(String jarFile, List<String> driverClasses) {}
}
