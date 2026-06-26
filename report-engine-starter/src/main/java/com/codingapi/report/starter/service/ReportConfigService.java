package com.codingapi.report.starter.service;

import com.codingapi.report.config.dto.ReportDTO;
import com.codingapi.report.core.Report;
import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.repository.PageQuery;
import com.codingapi.report.repository.PageResult;
import com.codingapi.report.repository.ReportRepository;
import com.codingapi.report.starter.converter.DataModelDtoAssembler;

/**
 * 报表业务：CRUD + 加载时富化数据模型视图。
 *
 * <p>仓库以领域 {@link Report} 存取；出入站用 {@link ReportDTO}（{@link Report#toDTO()} / {@link
 * Report#fromDTO}）。富化通过 {@link DataModelService#findDataModel} 取运行时模型（容错：模型缺失不阻断加载）， 由 {@link
 * DataModelDtoAssembler} 组装为前端视图。
 */
public class ReportConfigService {

    private final ReportRepository repository;
    private final DataModelService dataModelService;

    public ReportConfigService(ReportRepository repository, DataModelService dataModelService) {
        this.repository = repository;
        this.dataModelService = dataModelService;
    }

    public String save(ReportDTO dto) {
        return repository.save(Report.fromDTO(dto));
    }

    /** 加载并富化数据模型视图。 */
    public ReportDTO get(String id) {
        Report report = repository.find(id);
        if (report == null) return null;
        ReportDTO dto = report.toDTO();
        if (report.getDataModelId() != null) {
            DataModel dm = dataModelService.findDataModel(report.getDataModelId());
            if (dm != null) {
                dto.setDataModel(DataModelDtoAssembler.assemble(dm));
            }
        }
        return dto;
    }

    public void delete(String id) {
        repository.delete(id);
    }

    public PageResult<Report> page(int current, int pageSize) {
        return repository.page(new PageQuery(current, pageSize));
    }
}
