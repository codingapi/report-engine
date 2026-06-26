package com.codingapi.report.starter.service;

import static org.junit.jupiter.api.Assertions.*;

import com.codingapi.report.data.datasource.DataSourceTypeConfig;
import com.codingapi.report.data.datasource.type.DbDataSourceType;
import com.codingapi.report.dto.datasource.DataSourceTypeDTO;
import com.codingapi.report.repository.DataSourceTypeRepository;
import com.codingapi.report.repository.PageQuery;
import com.codingapi.report.repository.PageResult;
import com.codingapi.report.starter.properties.ReportProperties;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link DriverLoader} 测试：启动加载、热注册、去重、jar 缺失/非 Driver 类等场景。
 *
 * <p>用 {@link ToolProvider} 编译一个 stub {@link Driver}（{@code org.example.MyDriver}）打成 jar，验证
 * URLClassLoader 加载 + DriverShim 包装 + DriverManager 注册整条链路。
 */
class DriverLoaderTest {

    private static final String DRIVER_CLASS = "org.example.MyDriver";

    @TempDir Path tempDir;

    private Path driverDir;
    private FakeDataSourceTypeRepository repository;
    private ReportProperties properties;
    private DriverLoader loader;

    @BeforeEach
    void setUp() throws IOException {
        driverDir = tempDir.resolve("drivers");
        Files.createDirectories(driverDir);
        properties = new ReportProperties();
        properties.getDriver().setDir(driverDir.toString());
        repository = new FakeDataSourceTypeRepository();
        loader = new DriverLoader(repository, properties);
    }

    @AfterEach
    void tearDown() throws SQLException {
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver d = drivers.nextElement();
            if (d instanceof DriverShim) {
                DriverManager.deregisterDriver(d);
            }
        }
    }

    @Test
    void loadOnStartupNoOpWhenDriverDirMissing() {
        properties.getDriver().setDir(tempDir.resolve("does-not-exist").toString());
        saveDbConfig("mysql.jar", DRIVER_CLASS);
        loader.loadOnStartup();
        assertEquals(0, loader.registeredCount());
    }

    @Test
    void loadOnStartupNoOpWhenDriverDirEmpty() {
        loader.loadOnStartup();
        assertEquals(0, loader.registeredCount());
    }

    @Test
    void loadOnStartupRegistersFromConfigs() throws IOException, SQLException {
        Path jar = buildStubDriverJar(driverDir.resolve("stub.jar"));
        saveDbConfig(jar.getFileName().toString(), DRIVER_CLASS);

        loader.loadOnStartup();

        assertTrue(loader.isRegistered(DRIVER_CLASS));
        assertEquals(1, loader.registeredCount());
        DriverShim shim = loader.registeredShim(DRIVER_CLASS);
        assertNotNull(shim);
        assertSame(DRIVER_CLASS, shim.delegate().getClass().getName());
        assertRegisteredInDriverManager(shim);
    }

    @Test
    void loadOnStartupIsIdempotent() throws IOException, SQLException {
        Path jar = buildStubDriverJar(driverDir.resolve("stub.jar"));
        saveDbConfig(jar.getFileName().toString(), DRIVER_CLASS);

        loader.loadOnStartup();
        loader.loadOnStartup();
        loader.loadOnStartup();

        assertEquals(1, loader.registeredCount());
        assertEquals(1, countShimsInDriverManager());
    }

    @Test
    void loadOnStartupSkipsMissingJarSilently() {
        saveDbConfig("missing.jar", DRIVER_CLASS);
        loader.loadOnStartup();
        assertEquals(0, loader.registeredCount());
    }

    @Test
    void loadOnStartupSkipsNonDbConfigs() throws IOException {
        Path jar = buildStubDriverJar(driverDir.resolve("stub.jar"));
        // 非 DB 类型（type=null）应被跳过
        repository.save(DataSourceTypeConfig.builder()
                .id(UUID.randomUUID().toString())
                .name("non-db")
                .type(null)
                .build());
        saveDbConfig(jar.getFileName().toString(), DRIVER_CLASS);

        loader.loadOnStartup();

        assertEquals(1, loader.registeredCount());
    }

    @Test
    void loadOnStartupSkipsConfigWithBlankDriverClass() throws IOException {
        Path jar = buildStubDriverJar(driverDir.resolve("stub.jar"));
        saveDbConfig(jar.getFileName().toString(), "  ");
        loader.loadOnStartup();
        assertEquals(0, loader.registeredCount());
    }

    @Test
    void registerDriverHotRegisters() throws IOException, SQLException {
        Path jar = buildStubDriverJar(driverDir.resolve("stub.jar"));
        Driver registered = loader.registerDriver(jar.getFileName().toString(), DRIVER_CLASS);
        assertNotNull(registered);
        assertTrue(loader.isRegistered(DRIVER_CLASS));
        assertRegisteredInDriverManager((DriverShim) registered);
    }

    @Test
    void registerDriverDedupesByDriverClass() throws IOException, SQLException {
        Path jar = buildStubDriverJar(driverDir.resolve("stub.jar"));
        Driver first = loader.registerDriver(jar.getFileName().toString(), DRIVER_CLASS);
        Driver second = loader.registerDriver(jar.getFileName().toString(), DRIVER_CLASS);
        assertSame(first, second);
        assertEquals(1, loader.registeredCount());
        assertEquals(1, countShimsInDriverManager());
    }

    @Test
    void registerDriverAcceptsAbsoluteJarPath() throws IOException {
        Path jar = buildStubDriverJar(driverDir.resolve("stub.jar"));
        Driver registered = loader.registerDriver(jar.toString(), DRIVER_CLASS);
        assertNotNull(registered);
        assertTrue(loader.isRegistered(DRIVER_CLASS));
    }

    @Test
    void registerDriverReturnsNullForBlankClass() throws IOException {
        Path jar = buildStubDriverJar(driverDir.resolve("stub.jar"));
        Driver registered = loader.registerDriver(jar.getFileName().toString(), "  ");
        assertNull(registered);
        assertEquals(0, loader.registeredCount());
    }

    @Test
    void registerDriverThrowsForMissingJar() {
        IOException ex = assertThrows(
                IOException.class,
                () -> loader.registerDriver("missing.jar", DRIVER_CLASS));
        assertTrue(ex.getMessage().contains("driver jar not found"));
    }

    @Test
    void registerDriverThrowsForMissingClass() throws IOException {
        Path jar = buildJar(
                driverDir.resolve("empty.jar"),
                Map.of("META-INF/services/java.sql.Driver", "org.nope.Driver\n".getBytes(StandardCharsets.UTF_8)));
        IOException ex = assertThrows(
                IOException.class,
                () -> loader.registerDriver(jar.getFileName().toString(), "org.nope.Driver"));
        assertTrue(ex.getMessage().contains("driver class not found"));
    }

    @Test
    void registerDriverThrowsForNonDriverClass() throws IOException {
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        Path compiled = compileClass(classesDir, "org/example/NotADriver.java", """
                package org.example;
                public class NotADriver {
                    public NotADriver() {}
                }
                """);
        if (compiled == null) return;
        byte[] classBytes = Files.readAllBytes(compiled);
        Path jar = buildJar(driverDir.resolve("notdriver.jar"), Map.of("org/example/NotADriver.class", classBytes));

        IOException ex = assertThrows(
                IOException.class,
                () -> loader.registerDriver(jar.getFileName().toString(), "org.example.NotADriver"));
        assertTrue(ex.getMessage().contains("not a java.sql.Driver"));
    }

    // ─── helpers ───────────────────────────────────────────

    private void saveDbConfig(String jarFile, String driverClass) {
        DataSourceTypeDTO dto = new DataSourceTypeDTO(null, "stub", "DB", jarFile, driverClass, 0L, 0L);
        repository.save(DataSourceTypeConfig.fromDTO(dto));
    }

    private static int countShimsInDriverManager() throws SQLException {
        int count = 0;
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            if (drivers.nextElement() instanceof DriverShim) {
                count++;
            }
        }
        return count;
    }

    private static void assertRegisteredInDriverManager(DriverShim shim) throws SQLException {
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        boolean found = false;
        while (drivers.hasMoreElements()) {
            if (drivers.nextElement() == shim) {
                found = true;
                break;
            }
        }
        assertTrue(found, "shim should be registered in DriverManager");
    }

    private Path buildStubDriverJar(Path target) throws IOException {
        Path classesDir = tempDir.resolve("stub-classes");
        Files.createDirectories(classesDir);
        Path compiled = compileClass(classesDir, "org/example/MyDriver.java", """
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
        if (compiled == null) {
            return null;
        }
        byte[] classBytes = Files.readAllBytes(compiled);
        return buildJar(target, Map.of("org/example/MyDriver.class", classBytes));
    }

    private static Path buildJar(Path target, Map<String, byte[]> entries) throws IOException {
        Files.createDirectories(target.getParent());
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

    private static Path compileClass(Path classesDir, String relPath, String source) throws IOException {
        if (ToolProvider.getSystemJavaCompiler() == null) {
            return null; // JRE-only environment
        }
        Path srcFile = classesDir.resolve(relPath);
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, source);
        int rc = ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-d", classesDir.toString(), srcFile.toString());
        assertEquals(0, rc, "stub should compile: " + relPath);
        String classRel = relPath.substring(0, relPath.length() - ".java".length()) + ".class";
        return classesDir.resolve(classRel);
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
