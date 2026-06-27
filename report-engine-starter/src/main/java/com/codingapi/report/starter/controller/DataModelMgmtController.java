package com.codingapi.report.starter.controller;

import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.dto.datamodel.DataModelDTO;
import com.codingapi.report.dto.datamodel.RelationshipDTO;
import com.codingapi.report.repository.PageResult;
import com.codingapi.report.starter.service.DataModelService;
import com.codingapi.springboot.framework.dto.response.MultiResponse;
import com.codingapi.springboot.framework.dto.response.SingleResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据模型管理 API：CRUD 可复用的数据模型配置（含内嵌的连接/数据集/关系）。
 *
 * <p>仅做 HTTP 编排，业务（凭证脱敏/merge/加密、CRUD）下沉 {@link DataModelService}。
 */
@RestController
@RequestMapping("/api/datamodels")
@ConditionalOnClass(RestController.class)
public class DataModelMgmtController {

    private final DataModelService dataModelService;

    public DataModelMgmtController(DataModelService dataModelService) {
        this.dataModelService = dataModelService;
    }

    @GetMapping
    public MultiResponse<DataModelBrief> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResult<DataModel> result = dataModelService.page(current, pageSize);
        List<DataModelBrief> briefs =
                result.content().stream()
                        .map(
                                c ->
                                        new DataModelBrief(
                                                c.getId(),
                                                c.getName() != null ? c.getName() : "未命名模型",
                                                c.getStatus() != null ? c.getStatus().name() : null,
                                                c.getCreateTime(),
                                                c.getUpdateTime()))
                        .toList();
        return MultiResponse.of(briefs, result.total());
    }

    @GetMapping("/{id}")
    public SingleResponse<DataModelDTO> get(@PathVariable String id) {
        return SingleResponse.of(dataModelService.getMasked(id));
    }

    @PostMapping
    public SingleResponse<String> save(@RequestBody DataModelDTO dto) {
        return SingleResponse.of(dataModelService.save(dto));
    }

    @DeleteMapping("/{id}")
    public SingleResponse<Void> delete(@PathVariable String id) {
        dataModelService.delete(id);
        return SingleResponse.of(null);
    }

    /** 发布数据模型（草稿 → 已发布），已发布的保持不变。 */
    @PostMapping("/{id}/publish")
    public SingleResponse<Void> publish(@PathVariable String id) {
        dataModelService.publish(id);
        return SingleResponse.of(null);
    }

    /** 撤销发布（已发布 → 草稿）。 */
    @PostMapping("/{id}/unpublish")
    public SingleResponse<Void> unpublish(@PathVariable String id) {
        dataModelService.unpublish(id);
        return SingleResponse.of(null);
    }

    @PutMapping("/relationships")
    public SingleResponse<Void> saveRelationships(
            @RequestParam String dataModelId, @RequestBody List<RelationshipDTO> relationships) {
        dataModelService.saveRelationships(dataModelId, relationships);
        return SingleResponse.of(null);
    }

    public record DataModelBrief(
            String id, String name, String status, long createTime, long updateTime) {}
}
