package com.example.report.repository;

import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.data.datamodel.DataModelStatus;
import com.codingapi.report.repository.DataModelRepository;
import com.codingapi.report.repository.PageQuery;
import com.codingapi.report.repository.PageResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link DataModelRepository} 的内存实现（example 演示用）。
 *
 * <p>以领域 {@link DataModel} 保存；进程内存储，重启丢失。config 明文存内存（演示不落盘，故不加密）。 生产环境应由使用方提供持久化实现（落盘加密在该层处理）。
 */
public class InMemoryDataModelRepository implements DataModelRepository {

    private final ConcurrentHashMap<String, DataModel> store = new ConcurrentHashMap<>();

    @Override
    public String save(DataModel dataModel) {
        String id =
                dataModel.getId() != null && !dataModel.getId().isBlank()
                        ? dataModel.getId()
                        : UUID.randomUUID().toString();
        dataModel.setId(id);

        long now = System.currentTimeMillis();
        DataModel existing = store.get(id);
        dataModel.setCreateTime(existing != null ? existing.getCreateTime() : now);
        dataModel.setUpdateTime(now);
        if (dataModel.getStatus() == null) dataModel.setStatus(DataModelStatus.DRAFT);
        if (dataModel.getDatasets() == null) dataModel.setDatasets(List.of());
        if (dataModel.getRelationships() == null) dataModel.setRelationships(List.of());

        store.put(id, dataModel);
        return id;
    }

    @Override
    public DataModel find(String id) {
        return store.get(id);
    }

    @Override
    public PageResult<DataModel> page(PageQuery query) {
        int current = query.current();
        int pageSize = query.pageSize();
        List<DataModel> all = new ArrayList<>(store.values());
        all.sort((a, b) -> Long.compare(b.getCreateTime(), a.getCreateTime()));
        long total = all.size();
        int from = (int) Math.min((long) (current - 1) * pageSize, total);
        int to = (int) Math.min((long) from + pageSize, total);
        List<DataModel> pageList = all.subList(from, to);
        return new PageResult<>(pageList, total);
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }
}
