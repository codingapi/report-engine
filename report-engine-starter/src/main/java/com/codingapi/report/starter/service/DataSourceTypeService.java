package com.codingapi.report.starter.service;

import com.codingapi.report.data.datasource.DataSourceTypeConfig;
import com.codingapi.report.dto.datasource.DataSourceTypeDTO;
import com.codingapi.report.repository.DataSourceTypeRepository;
import com.codingapi.report.repository.PageQuery;
import com.codingapi.report.repository.PageResult;

public class DataSourceTypeService {

    private final DataSourceTypeRepository repository;

    public DataSourceTypeService(DataSourceTypeRepository repository) {
        this.repository = repository;
    }

    public PageResult<DataSourceTypeConfig> page(int current, int pageSize) {
        return repository.page(new PageQuery(current, pageSize));
    }

    public DataSourceTypeDTO get(String id) {
        DataSourceTypeConfig config = repository.find(id);
        return config != null ? config.toDTO() : null;
    }

    public String save(DataSourceTypeDTO dto) {
        return repository.save(DataSourceTypeConfig.fromDTO(dto));
    }

    public void delete(String id) {
        repository.delete(id);
    }
}
