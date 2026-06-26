package com.codingapi.report.starter.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.codingapi.report.data.datasource.DataSourceTypeConfig;
import com.codingapi.report.dto.datasource.DataSourceTypeDTO;
import com.codingapi.report.repository.DataSourceTypeRepository;
import com.codingapi.report.repository.PageQuery;
import com.codingapi.report.repository.PageResult;
import com.codingapi.report.starter.controller.DataSourceTypeController.DataSourceTypeBrief;
import com.codingapi.report.starter.controller.DataSourceTypeController.DriverJarUploadResponse;
import com.codingapi.report.starter.properties.ReportProperties;
import com.codingapi.report.starter.service.DataSourceTypeService;
import com.codingapi.springboot.framework.dto.response.MultiResponse;
import com.codingapi.springboot.framework.dto.response.SingleResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

class DataSourceTypeControllerTest {

    @TempDir Path tempDir;

    private FakeDataSourceTypeRepository repository;
    private ReportProperties properties;
    private DataSourceTypeService service;
    private DataSourceTypeController controller;

    @BeforeEach
    void setUp() {
        properties = new ReportProperties();
        properties.getDriver().setDir(tempDir.resolve("drivers").toString());
        repository = new FakeDataSourceTypeRepository();
        service = new DataSourceTypeService(repository, properties);
        controller = new DataSourceTypeController(service);
    }

    @Test
    void saveAndGetMappingRoundTrip() {
        DataSourceTypeDTO dto =
                new DataSourceTypeDTO(
                        null, "MySQL 8", "DB", "/drivers/mysql.jar", "com.mysql.cj.Driver", 0L, 0L);

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
                new DataSourceTypeDTO(
                        null, "PG", "DB", "/drivers/pg.jar", "org.postgresql.Driver", 0L, 0L);
        String id = controller.save(dto).getData();

        Thread.sleep(5);

        DataSourceTypeDTO update =
                new DataSourceTypeDTO(
                        id, "PG-Updated", "DB", "/drivers/pg.jar", "org.postgresql.Driver", 0L, 0L);
        controller.save(update);

        DataSourceTypeDTO loaded = controller.get(id).getData();
        assertEquals("PG-Updated", loaded.name());
        assertTrue(loaded.updateTime() >= loaded.createTime());
    }

    @Test
    void listReturnsBriefsPaginated() {
        for (int i = 0; i < 12; i++) {
            controller.save(
                    new DataSourceTypeDTO(
                            null,
                            "type-" + i,
                            "DB",
                            "/drivers/" + i + ".jar",
                            "com.example.Driver" + i,
                            0L,
                            0L));
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
        String id =
                controller.save(new DataSourceTypeDTO(null, "x", "DB", "j", "d", 0L, 0L)).getData();
        assertNotNull(controller.get(id).getData());

        SingleResponse<Void> del = controller.delete(id);
        assertTrue(del.isSuccess());
        assertNull(controller.get(id).getData());
    }

    @Test
    void saveRejectsUnknownKind() {
        DataSourceTypeDTO bad = new DataSourceTypeDTO(null, "bad", "WHAT", "j", "d", 0L, 0L);
        assertThrows(IllegalArgumentException.class, () -> controller.save(bad));
    }

    @Test
    void uploadDriverJarReadsServiceFile() throws IOException {
        String serviceContent = "com.mysql.cj.Driver\n# comment line\norg.postgresql.Driver\n\n";
        Path jar =
                buildJar(
                        tempDir.resolve("src-service.jar"),
                        Map.of(
                                "META-INF/services/java.sql.Driver",
                                serviceContent.getBytes(StandardCharsets.UTF_8),
                                "com/mysql/cj/Driver.class",
                                new byte[] {0x00}));

        SingleResponse<DriverJarUploadResponse> resp =
                controller.uploadDriverJar(mockMultipartFile("mysql.jar", jar));
        assertTrue(resp.isSuccess());
        DriverJarUploadResponse data = resp.getData();
        assertNotNull(data);
        assertEquals("mysql.jar", data.jarFile());
        assertEquals(List.of("com.mysql.cj.Driver", "org.postgresql.Driver"), data.driverClasses());

        Path stored = tempDir.resolve("drivers").resolve("mysql.jar");
        assertTrue(Files.isRegularFile(stored), "jar should be stored in driver.dir");
    }

    @Test
    void uploadDriverJarScansClassesWhenNoServiceFile() throws IOException {
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        Path compiled = compileStubDriver(classesDir);
        if (compiled == null) {
            return; // JRE-only environment, skip
        }
        byte[] classBytes = Files.readAllBytes(compiled);
        Path jar =
                buildJar(
                        tempDir.resolve("scan.jar"),
                        Map.of("org/example/MyDriver.class", classBytes));

        SingleResponse<DriverJarUploadResponse> resp =
                controller.uploadDriverJar(mockMultipartFile("scan.jar", jar));
        assertTrue(resp.isSuccess());
        DriverJarUploadResponse data = resp.getData();
        assertNotNull(data);
        assertEquals("scan.jar", data.jarFile());
        assertEquals(List.of("org.example.MyDriver"), data.driverClasses());
    }

    @Test
    void uploadDriverJarWithNoDriversReturnsEmpty() throws IOException {
        Path jar =
                buildJar(
                        tempDir.resolve("empty.jar"),
                        Map.of("org/example/NotADriver.class", new byte[] {0x00}));

        SingleResponse<DriverJarUploadResponse> resp =
                controller.uploadDriverJar(mockMultipartFile("empty.jar", jar));
        assertTrue(resp.isSuccess());
        DriverJarUploadResponse data = resp.getData();
        assertNotNull(data);
        assertEquals("empty.jar", data.jarFile());
        assertTrue(data.driverClasses().isEmpty());
    }

    @Test
    void uploadDriverJarRejectsEmptyFile() {
        MultipartFile empty = mock(MultipartFile.class);
        when(empty.isEmpty()).thenReturn(true);
        when(empty.getOriginalFilename()).thenReturn("x.jar");
        assertThrows(IOException.class, () -> controller.uploadDriverJar(empty));
    }

    @Test
    void uploadDriverJarSanitizesPathTraversal() throws IOException {
        Path jar =
                buildJar(
                        tempDir.resolve("svc.jar"),
                        Map.of(
                                "META-INF/services/java.sql.Driver",
                                "com.example.Driver\n".getBytes(StandardCharsets.UTF_8)));
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("../evil.jar");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(Files.readAllBytes(jar)));

        SingleResponse<DriverJarUploadResponse> resp = controller.uploadDriverJar(file);
        assertTrue(resp.isSuccess());
        assertEquals("evil.jar", resp.getData().jarFile());
        assertFalse(Files.exists(tempDir.resolve("evil.jar")));
        assertTrue(Files.exists(tempDir.resolve("drivers").resolve("evil.jar")));
    }

    private static MultipartFile mockMultipartFile(String filename, Path content)
            throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getInputStream())
                .thenReturn(new ByteArrayInputStream(Files.readAllBytes(content)));
        return file;
    }

    private static Path buildJar(Path target, Map<String, byte[]> entries) throws IOException {
        Files.createDirectories(target.getParent());
        // Use LinkedHashMap to preserve insertion order (e.g., service file before classes).
        Map<String, byte[]> ordered = new LinkedHashMap<>(entries);
        try (OutputStream out = Files.newOutputStream(target);
                JarOutputStream jar = new JarOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : ordered.entrySet()) {
                jar.putNextEntry(new JarEntry(e.getKey()));
                jar.write(e.getValue());
                jar.closeEntry();
            }
        }
        return target;
    }

    private static Path compileStubDriver(Path classesDir) throws IOException {
        if (ToolProvider.getSystemJavaCompiler() == null) {
            return null; // JRE-only environment
        }
        Path srcFile = classesDir.resolve("org/example/MyDriver.java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(
                srcFile,
                """
                package org.example;
                import java.sql.*;
                import java.util.Properties;
                import java.util.logging.Logger;
                public class MyDriver implements Driver {
                    public Connection connect(String url, Properties info) { return null; }
                    public boolean acceptsURL(String url) { return false; }
                    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return null; }
                    public int getMajorVersion() { return 1; }
                    public int getMinorVersion() { return 0; }
                    public boolean jdbcCompliant() { return false; }
                    public Logger getParentLogger() { return null; }
                }
                """);
        int rc =
                ToolProvider.getSystemJavaCompiler()
                        .run(null, null, null, "-d", classesDir.toString(), srcFile.toString());
        assertEquals(0, rc, "stub driver should compile");
        return classesDir.resolve("org/example/MyDriver.class");
    }

    static class FakeDataSourceTypeRepository implements DataSourceTypeRepository {
        final ConcurrentHashMap<String, DataSourceTypeConfig> store = new ConcurrentHashMap<>();

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
