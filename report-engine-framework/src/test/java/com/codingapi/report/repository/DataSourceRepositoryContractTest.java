package com.codingapi.report.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.type.DbDataSourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/**
 * {@link DataSourceRepository} 契约测试：用 framework 内的内存实现验证 save/find/page/delete 语义。
 *
 * <p>真正的 {@code InMemoryDataSourceRepository} 在 example 模块，这里用同范式的本地实现确保 framework
 * 层契约可独立验证（使用方实现遵循同契约即可）。
 */
class DataSourceRepositoryContractTest {

    @Test
    void saveAssignsIdAndTimestampsOnCreate() {
        DataSourceRepository repo = new TestInMemoryRepo();
        DataSource source =
                DataSource.builder()
                        .name("HR 库")
                        .type(new DbDataSourceType(null, null))
                        .typeConfigId("dst-mysql-8")
                        .config(java.util.Map.of("url", "jdbc:mysql://localhost/hr"))
                        .build();
        String id = repo.save(source);

        assertNotNull(id);
        DataSource saved = repo.find(id);
        assertEquals(id, saved.getId());
        assertEquals("HR 库", saved.getName());
        assertEquals("dst-mysql-8", saved.getTypeConfigId());
        assertEquals("jdbc:mysql://localhost/hr", saved.getConfig().get("url"));
        assertTrue(saved.getCreateTime() > 0);
        assertEquals(saved.getCreateTime(), saved.getUpdateTime());
    }

    @Test
    void savePreservesIdAndCreateTimeOnUpdate() throws Exception {
        DataSourceRepository repo = new TestInMemoryRepo();
        DataSource source =
                DataSource.builder()
                        .name("薪资库")
                        .type(new DbDataSourceType(null, null))
                        .typeConfigId("dst-pg")
                        .build();
        String id = repo.save(source);
        long created = repo.find(id).getCreateTime();

        Thread.sleep(2);
        source.setName("薪资库 (改)");
        source.setTypeConfigId("dst-pg-15");
        repo.save(source);

        DataSource updated = repo.find(id);
        assertEquals(id, updated.getId());
        assertEquals("薪资库 (改)", updated.getName());
        assertEquals("dst-pg-15", updated.getTypeConfigId());
        assertEquals(created, updated.getCreateTime());
        assertTrue(updated.getUpdateTime() >= created);
    }

    @Test
    void pageReturnsByCreateTimeDesc() {
        DataSourceRepository repo = new TestInMemoryRepo();
        for (int i = 0; i < 5; i++) {
            repo.save(
                    DataSource.builder()
                            .name("数据源-" + i)
                            .type(new DbDataSourceType(null, null))
                            .build());
            try {
                Thread.sleep(1);
            } catch (InterruptedException ignored) {
            }
        }

        PageResult<DataSource> p1 = repo.page(new PageQuery(1, 2));
        assertEquals(5, p1.total());
        assertEquals(2, p1.content().size());
        PageResult<DataSource> p2 = repo.page(new PageQuery(3, 2));
        assertEquals(1, p2.content().size());

        List<DataSource> all = new ArrayList<>();
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
        DataSourceRepository repo = new TestInMemoryRepo();
        repo.save(
                DataSource.builder()
                        .name("仅一个")
                        .type(new DbDataSourceType(null, null))
                        .build());
        PageResult<DataSource> p = repo.page(new PageQuery(0, 0));
        assertEquals(1, p.total());
        assertEquals(1, p.content().size());
    }

    @Test
    void deleteRemovesSource() {
        DataSourceRepository repo = new TestInMemoryRepo();
        String id =
                repo.save(
                        DataSource.builder()
                                .name("待删")
                                .type(new DbDataSourceType(null, null))
                                .build());
        assertNotNull(repo.find(id));
        repo.delete(id);
        assertNull(repo.find(id));
    }

    @Test
    void findMissingReturnsNull() {
        DataSourceRepository repo = new TestInMemoryRepo();
        assertNull(repo.find("not-exist"));
    }

    /** 同 {@code InMemoryDataSourceRepository} 范式的内存实现，仅供 framework 契约测试。 */
    private static class TestInMemoryRepo implements DataSourceRepository {
        private final ConcurrentHashMap<String, DataSource> store = new ConcurrentHashMap<>();

        @Override
        public String save(DataSource source) {
            String id =
                    source.getId() != null && !source.getId().isBlank()
                            ? source.getId()
                            : UUID.randomUUID().toString();
            source.setId(id);
            long now = System.currentTimeMillis();
            DataSource existing = store.get(id);
            source.setCreateTime(existing != null ? existing.getCreateTime() : now);
            source.setUpdateTime(now);
            store.put(id, source);
            return id;
        }

        @Override
        public DataSource find(String id) {
            return store.get(id);
        }

        @Override
        public PageResult<DataSource> page(PageQuery query) {
            int current = query.current();
            int pageSize = query.pageSize();
            List<DataSource> all = new ArrayList<>(store.values());
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
