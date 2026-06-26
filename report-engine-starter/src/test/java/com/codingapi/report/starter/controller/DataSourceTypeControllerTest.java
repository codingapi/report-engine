package com.codingapi.report.starter.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.codingapi.report.data.datasource.DataSourceTypeConfig;
import com.codingapi.report.dto.datasource.DataSourceTypeDTO;
import com.codingapi.report.repository.DataSourceTypeRepository;
import com.codingapi.report.repository.PageQuery;
import com.codingapi.report.repository.PageResult;
import com.codingapi.report.starter.controller.DataSourceTypeController.DataSourceTypeBrief;
import com.codingapi.report.starter.service.DataSourceTypeService;
import com.codingapi.springboot.framework.dto.response.MultiResponse;
import com.codingapi.springboot.framework.dto.response.SingleResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataSourceTypeControllerTest {

    private FakeDataSourceTypeRepository repository;
    private DataSourceTypeService service;
    private DataSourceTypeController controller;

    @BeforeEach
    void setUp() {
        repository = new FakeDataSourceTypeRepository();
        service = new DataSourceTypeService(repository);
        controller = new DataSourceTypeController(service);
    }

    @Test
    void saveAndGetMappingRoundTrip() {
        DataSourceTypeDTO dto =
                new DataSourceTypeDTO(null, "MySQL 8", "DB", "/drivers/mysql.jar", "com.mysql.cj.Driver", 0L, 0L);

        SingleResponse<String> saveResp = controller.save(dto);
        assertTrue(saveResp.isSuccess());
        String id = saveResp.getData();
        assertNotNull(id);

        SingleResponse<DataSourceTypeDTO> getResp = controller.get(id);
        assertTrue(getResp.isSuccess());
        DataSourceTypeDTO loaded = getResp.getData();
        assertNotNull(loaded);
        assertEquals("MySQL 8", loaded.name());
        assertEquals("DB", loaded.kind());
        assertEquals("/drivers/mysql.jar", loaded.jarFile());
        assertEquals("com.mysql.cj.Driver", loaded.driverClass());
        assertTrue(loaded.createTime() > 0);
        assertTrue(loaded.updateTime() > 0);
    }

    @Test
    void saveUpdatePreservesCreateTime() throws Exception {
        DataSourceTypeDTO dto =
                new DataSourceTypeDTO(null, "PG", "DB", "/drivers/pg.jar", "org.postgresql.Driver", 0L, 0L);
        String id = controller.save(dto).getData();

        Thread.sleep(5);

        DataSourceTypeDTO update =
                new DataSourceTypeDTO(id, "PG-Updated", "DB", "/drivers/pg.jar", "org.postgresql.Driver", 0L, 0L);
        controller.save(update);

        DataSourceTypeDTO loaded = controller.get(id).getData();
        assertEquals("PG-Updated", loaded.name());
        assertTrue(loaded.updateTime() >= loaded.createTime());
    }

    @Test
    void listReturnsBriefsPaginated() {
        for (int i = 0; i < 12; i++) {
            controller.save(new DataSourceTypeDTO(
                    null, "type-" + i, "DB", "/drivers/" + i + ".jar", "com.example.Driver" + i, 0L, 0L));
        }

        MultiResponse<DataSourceTypeBrief> page1 = controller.list(1, 10);
        assertTrue(page1.isSuccess());
        assertEquals(12, page1.getData().getTotal());
        assertEquals(10, page1.getData().getList().size());

        MultiResponse<DataSourceTypeBrief> page2 = controller.list(2, 10);
        assertEquals(2, page2.getData().getList().size());

        DataSourceTypeBrief brief = page1.getData().getList().iterator().next();
        assertNotNull(brief.id());
        assertNotNull(brief.name());
        assertEquals("DB", brief.kind());
        assertTrue(brief.createTime() > 0);
    }

    @Test
    void listUsesDefaultsForMissingParams() {
        controller.save(new DataSourceTypeDTO(null, "solo", "DB", "j", "d", 0L, 0L));
        MultiResponse<DataSourceTypeBrief> resp = controller.list(1, 10);
        assertEquals(1, resp.getData().getTotal());
    }

    @Test
    void getMissingReturnsNullData() {
        SingleResponse<DataSourceTypeDTO> resp = controller.get("nope");
        assertTrue(resp.isSuccess());
        assertNull(resp.getData());
    }

    @Test
    void deleteRemovesRecord() {
        String id = controller.save(
                new DataSourceTypeDTO(null, "x", "DB", "j", "d", 0L, 0L)).getData();
        assertNotNull(controller.get(id).getData());

        SingleResponse<Void> del = controller.delete(id);
        assertTrue(del.isSuccess());
        assertNull(controller.get(id).getData());
    }

    @Test
    void saveRejectsUnknownKind() {
        DataSourceTypeDTO bad =
                new DataSourceTypeDTO(null, "bad", "WHAT", "j", "d", 0L, 0L);
        assertThrows(IllegalArgumentException.class, () -> controller.save(bad));
    }

    static class FakeDataSourceTypeRepository implements DataSourceTypeRepository {
        final ConcurrentHashMap<String, DataSourceTypeConfig> store = new ConcurrentHashMap<>();

        @Override
        public String save(DataSourceTypeConfig config) {
            String id = config.getId() != null && !config.getId().isBlank()
                    ? config.getId()
                    : UUID.randomUUID().toString();
            config.setId(id);
            long now = System.currentTimeMillis();
            DataSourceTypeConfig existing = store.get(id);
            config.setCreateTime(existing != null ? existing.getCreateTime() : now);
            config.setUpdateTime(now);
            store.put(id, config);
            return id;
        }

        @Override
        public DataSourceTypeConfig find(String id) {
            return store.get(id);
        }

        @Override
        public PageResult<DataSourceTypeConfig> page(PageQuery query) {
            List<DataSourceTypeConfig> all = new ArrayList<>(store.values());
            all.sort((a, b) -> Long.compare(b.getCreateTime(), a.getCreateTime()));
            long total = all.size();
            int from = (int) Math.min((long) (query.current() - 1) * query.pageSize(), total);
            int to = (int) Math.min((long) from + query.pageSize(), total);
            return new PageResult<>(all.subList(from, to), total);
        }

        @Override
        public void delete(String id) {
            store.remove(id);
        }
    }
}
