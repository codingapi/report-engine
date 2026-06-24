package com.codingapi.report.starter.controller;

import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.springboot.framework.dto.response.MultiResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据模型列表：供前端创建报表时选择数据模型。
 *
 * <p>注入容器中所有 {@link DataModel} Bean（使用方可注册多个数据模型），返回 id + name 简要信息。
 */
@RestController
@RequestMapping("/api/datamodels")
@ConditionalOnClass(RestController.class)
public class DataModelController {

    private final List<DataModel> dataModels;

    public DataModelController(List<DataModel> dataModels) {
        this.dataModels = dataModels;
    }

    /** 数据模型列表（id + name）。 */
    @GetMapping
    public MultiResponse<DataModelBrief> list() {
        List<DataModelBrief> briefs =
                dataModels.stream()
                        .map(dm -> new DataModelBrief(dm.getId(), dm.getName()))
                        .toList();
        return MultiResponse.of(briefs);
    }

    public record DataModelBrief(String id, String name) {}
}
