package com.codingapi.report.starter.controller;

import com.codingapi.report.starter.dto.DatasetDtos.DatasetDTO;
import com.codingapi.report.starter.dto.DatasetDtos.PreviewDTO;
import com.codingapi.report.starter.service.DataModelService;
import com.codingapi.report.starter.service.DataSourceService;
import com.codingapi.springboot.framework.dto.response.MultiResponse;
import com.codingapi.springboot.framework.dto.response.SingleResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据集元数据 API：列出数据集（含字段定义）、预览前 N 行，供前端左面板树形展示。
 *
 * <p>列表走 {@link DataModelService}（模型视图），预览走 {@link DataSourceService}（提取器派发）。
 */
@RestController
@RequestMapping("/api/datasets")
@ConditionalOnClass(RestController.class)
public class DatasetController {

    private final DataModelService dataModelService;
    private final DataSourceService dataSourceService;

    public DatasetController(
            DataModelService dataModelService, DataSourceService dataSourceService) {
        this.dataModelService = dataModelService;
        this.dataSourceService = dataSourceService;
    }

    @GetMapping
    public MultiResponse<DatasetDTO> list(@RequestParam String dataModelId) {
        return MultiResponse.of(dataModelService.listDatasets(dataModelId));
    }

    @GetMapping("/{id}/preview")
    public SingleResponse<PreviewDTO> preview(
            @PathVariable String id,
            @RequestParam String dataModelId,
            @RequestParam(defaultValue = "20") int limit) {
        return SingleResponse.of(dataSourceService.previewDataset(dataModelId, id, limit));
    }
}
