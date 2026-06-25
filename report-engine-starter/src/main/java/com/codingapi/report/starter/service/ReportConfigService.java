package com.codingapi.report.starter.service;

import com.codingapi.report.config.ReportConfig;
import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.repository.PageQuery;
import com.codingapi.report.repository.PageResult;
import com.codingapi.report.repository.ReportRepository;
import com.codingapi.report.starter.converter.DataModelDtoAssembler;

/**
 * 报表配置业务：CRUD + 加载时富化数据模型视图。
 *
 * <p>富化通过 {@link DataModelService#findDataModel} 取运行时模型（容错：模型缺失不阻断配置加载）， 再由 {@link
 * DataModelDtoAssembler} 组装为前端视图。
 */
public class ReportConfigService {

    private final ReportRepository repository;
    private final DataModelService dataModelService;

    public ReportConfigService(ReportRepository repository, DataModelService dataModelService) {
        this.repository = repository;
        this.dataModelService = dataModelService;
    }

    public String save(ReportConfig config) {
        return repository.save(config);
    }

    /** 加载配置并富化数据模型视图。 */
    public ReportConfig get(String id) {
        ReportConfig config = repository.find(id);
        if (config == null) return null;
        if (config.getDataModelId() != null) {
            DataModel dm = dataModelService.findDataModel(config.getDataModelId());
            if (dm != null) {
                config.setDataModel(DataModelDtoAssembler.assemble(dm));
            }
        }
        return config;
    }

    public void delete(String id) {
        repository.delete(id);
    }

    public PageResult<ReportConfig> page(int current, int pageSize) {
        return repository.page(new PageQuery(current, pageSize));
    }
}
