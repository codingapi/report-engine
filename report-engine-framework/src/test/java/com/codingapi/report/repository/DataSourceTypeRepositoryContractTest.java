package com.codingapi.report.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingapi.report.data.datasource.DataSourceTypeConfig;
import com.codingapi.report.data.datasource.type.DbDataSourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/**
 * {@link DataSourceTypeRepository} 契约测试：用 framework 内的内存实现验证 save/find/page/delete 语义。
 *
 * <p>真正的 {@code InMemoryDataSourceTypeRepository} 在 example 模块，这里用同范式的本地实现确保 framework
 * 层契约可独立验证（使用方实现遵循同契约即可）。
 */
class DataSourceTypeRepositoryContractTest {

    @Test
    void saveAssignsIdAndTimestampsOnCreate() {
        DataSourceTypeRepository repo = new TestInMemoryRepo();
        DataSourceTypeConfig config =
                DataSourceTypeConfig.builder()
                        .name("MySQL 驱动")
                        .type(new DbDataSourceType("/d/m.jar", "com.mysql.cj.jdbc.Driver"))
                        .build();
        String id = repo.save(config);

        assertNotNull(id);
        DataSourceTypeConfig saved = repo.find(id);
        assertEquals(id, saved.getId());
        assertEquals("MySQL 驱动", saved.getName());
        assertTrue(saved.getCreateTime() > 0);
        assertEquals(saved.getCreateTime(), saved.getUpdateTime());
    }

    @Test
    void savePreservesIdAndCreateTimeOnUpdate() throws Exception {
        DataSourceTypeRepository repo = new TestInMemoryRepo();
        DataSourceTypeConfig config =
                DataSourceTypeConfig.builder()
                        .name("PG 驱动")
                        .type(new DbDataSourceType("/d/p.jar", "org.postgresql.Driver"))
                        .build();
        String id = repo.save(config);
        long created = repo.find(id).getCreateTime();

        Thread.sleep(2);
        config.setName("PG 驱动 (改)");
        repo.save(config);

        DataSourceTypeConfig updated = repo.find(id);
        assertEquals(id, updated.getId());
        assertEquals("PG 驱动 (改)", updated.getName());
        assertEquals(created, updated.getCreateTime());
        assertTrue(updated.getUpdateTime() >= created);
    }

    @Test
    void pageReturnsByCreateTimeDesc() {
        DataSourceTypeRepository repo = new TestInMemoryRepo();
        for (int i = 0; i < 5; i++) {
            DataSourceTypeConfig c =
                    DataSourceTypeConfig.builder()
                            .name("驱动-" + i)
                            .type(new DbDataSourceType("/d/" + i + ".jar", "d.C" + i))
                            .build();
            repo.save(c);
            try {
                Thread.sleep(1);
            } catch (InterruptedException ignored) {
            }
        }

        PageResult<DataSourceTypeConfig> p1 = repo.page(new PageQuery(1, 2));
        assertEquals(5, p1.total());
        assertEquals(2, p1.content().size());
        PageResult<DataSourceTypeConfig> p2 = repo.page(new PageQuery(3, 2));
        assertEquals(1, p2.content().size());

        List<DataSourceTypeConfig> all = new ArrayList<>();
        all.addAll(p1.content());
        all.addAll(p2.content());
        for (int i = 1; i < all.size(); i++) {
            assertTrue(
                    all.get(i - 1).getCreateTime() >= all.get(i).getCreateTime(),
                    "应按 createTime 降序");
        }
    }

    @Test
    void pageQueryNormalizesInvalidPaging() {
        DataSourceTypeRepository repo = new TestInMemoryRepo();
        repo.save(
                DataSourceTypeConfig.builder()
                        .name("驱动")
                        .type(new DbDataSourceType("/d/x.jar", "d.X"))
                        .build());
        PageResult<DataSourceTypeConfig> p = repo.page(new PageQuery(0, 0));
        assertEquals(1, p.total());
        assertEquals(1, p.content().size());
    }

    @Test
    void deleteRemovesConfig() {
        DataSourceTypeRepository repo = new TestInMemoryRepo();
        String id =
                repo.save(
                        DataSourceTypeConfig.builder()
                                .name("驱动")
                                .type(new DbDataSourceType("/d/x.jar", "d.X"))
                                .build());
        assertNotNull(repo.find(id));
        repo.delete(id);
        assertNull(repo.find(id));
    }

    @Test
    void findMissingReturnsNull() {
        DataSourceTypeRepository repo = new TestInMemoryRepo();
        assertNull(repo.find("not-exist"));
    }

    /** 同 {@code InMemoryDataSourceTypeRepository} 范式的内存实现，仅供 framework 契约测试。 */
    private static class TestInMemoryRepo implements DataSourceTypeRepository {
        private final ConcurrentHashMap<String, DataSourceTypeConfig> store =
                new ConcurrentHashMap<>();

        @Override
        public String save(DataSourceTypeConfig config) {
            String id =
                    config.getId() != null && !config.getId().isBlank()
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
            int current = query.current();
            int pageSize = query.pageSize();
            List<DataSourceTypeConfig> all = new ArrayList<>(store.values());
            all.sort((a, b) -> Long.compare(b.getCreateTime(), a.getCreateTime()));
            long total = all.size();
            int from = (int) Math.min((long) (current - 1) * pageSize, total);
            int to = (int) Math.min((long) from + pageSize, total);
            return new PageResult<>(all.subList(from, to), total);
        }

        @Override
        public void delete(String id) {
            store.remove(id);
        }
    }
}
