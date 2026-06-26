package com.codingapi.report.starter.service;

import com.codingapi.report.data.datasource.DataSourceTypeConfig;
import com.codingapi.report.data.datasource.type.DbDataSourceType;
import com.codingapi.report.repository.DataSourceTypeRepository;
import com.codingapi.report.repository.PageQuery;
import com.codingapi.report.repository.PageResult;
import com.codingapi.report.starter.properties.ReportProperties;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * 外部 JDBC 驱动加载器（starter 组件）。
 *
 * <p>启动时（{@link ApplicationReadyEvent}）扫描 {@link ReportProperties.DriverProperty#getDir()} 下全部 jar，按仓库中
 * 每个 DB 类型配置的 {@code driverClass} 实例化驱动，用 {@link DriverShim} 包装后通过 {@link
 * DriverManager#registerDriver} 注册，使外部 jar 加载的驱动能被 DriverManager 正常发现。
 *
 * <p>新增 jar 时可调用 {@link #registerDriver(String, String)} 热注册。驱动按 {@code driverClass} 去重，同一驱动类只
 * 注册一次。{@code driver.dir} 不存在或为空、配置缺失、jar 缺失等情况下静默跳过（不抛异常）。
 */
public class DriverLoader {

    private static final Logger log = Logger.getLogger(DriverLoader.class.getName());

    private final DataSourceTypeRepository repository;
    private final ReportProperties properties;

    /** driverClass → 已注册的 shim（按 driverClass 去重）。 */
    private final Map<String, DriverShim> registered = new ConcurrentHashMap<>();

    /** jarPath → classloader（同一 jar 不重复打开）。 */
    private final Map<String, URLClassLoader> loaders = new ConcurrentHashMap<>();

    public DriverLoader(DataSourceTypeRepository repository, ReportProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** 启动时加载：遍历仓库中全部 DB 配置，按 driverClass 注册对应驱动。 */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void loadOnStartup() {
        Path driverDir = Path.of(properties.getDriver().getDir());
        if (!Files.isDirectory(driverDir)) {
            return;
        }
        for (DataSourceTypeConfig config : allConfigs()) {
            if (!(config.getType() instanceof DbDataSourceType db)) {
                continue;
            }
            try {
                registerDriver(db.jarFile(), db.driverClass());
            } catch (IOException e) {
                log.warning("skip driver config " + config.getId() + ": " + e.getMessage());
            }
        }
    }

    /**
     * 热注册：加载 {@code jarFile} → 实例化 {@code driverClass} → {@link DriverShim} 包装 → 注册到 {@link
     * DriverManager}。
     *
     * <p>已注册（按 {@code driverClass} 去重）则直接返回已有 shim，不重复注册。jar 不存在、driverClass 无法加载
     * 或实例化、注册失败时抛 {@link IOException}。{@code driverClass} 为空时返回 null。
     *
     * @return 注册的 {@link DriverShim}；driverClass 为空时返回 null
     */
    public synchronized Driver registerDriver(String jarFile, String driverClass) throws IOException {
        if (driverClass == null || driverClass.isBlank()) {
            return null;
        }
        DriverShim existing = registered.get(driverClass);
        if (existing != null) {
            return existing;
        }
        Path jarPath = resolveJar(jarFile);
        if (jarPath == null) {
            throw new IOException("driver jar not found: " + jarFile);
        }
        URLClassLoader loader;
        try {
            URL jarUrl = jarPath.toUri().toURL();
            loader = loaders.computeIfAbsent(
                    jarPath.toString(),
                    k -> new URLClassLoader(new URL[] {jarUrl}, getClass().getClassLoader()));
        } catch (java.net.MalformedURLException e) {
            throw new IOException("invalid jar url: " + jarPath, e);
        }
        Class<?> clazz;
        try {
            clazz = Class.forName(driverClass, true, loader);
        } catch (ClassNotFoundException e) {
            throw new IOException("driver class not found: " + driverClass, e);
        }
        if (!Driver.class.isAssignableFrom(clazz)) {
            throw new IOException("class is not a java.sql.Driver: " + driverClass);
        }
        Driver driver;
        try {
            driver = (Driver) clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IOException("failed to instantiate driver: " + driverClass, e);
        }
        DriverShim shim = new DriverShim(driver);
        try {
            DriverManager.registerDriver(shim);
        } catch (SQLException e) {
            throw new IOException("failed to register driver: " + driverClass, e);
        }
        registered.put(driverClass, shim);
        return shim;
    }

    /** 是否已注册该 driverClass。 */
    public boolean isRegistered(String driverClass) {
        return driverClass != null && registered.containsKey(driverClass);
    }

    /** 已注册的驱动数量（测试 / 监控用）。 */
    public int registeredCount() {
        return registered.size();
    }

    /** 测试与诊断用：按 driverClass 取已注册的 shim（无则 null）。 */
    DriverShim registeredShim(String driverClass) {
        return registered.get(driverClass);
    }

    private Path resolveJar(String jarFile) {
        if (jarFile == null || jarFile.isBlank()) {
            return null;
        }
        Path direct = Path.of(jarFile);
        if (direct.isAbsolute() && Files.isRegularFile(direct)) {
            return direct;
        }
        Path driverDir = Path.of(properties.getDriver().getDir()).normalize();
        Path resolved = driverDir.resolve(jarFile).normalize();
        if (Files.isRegularFile(resolved)) {
            return resolved;
        }
        return Files.isRegularFile(direct) ? direct : null;
    }

    private List<DataSourceTypeConfig> allConfigs() {
        List<DataSourceTypeConfig> all = new ArrayList<>();
        int current = 1;
        int pageSize = 100;
        while (true) {
            PageResult<DataSourceTypeConfig> page = repository.page(new PageQuery(current, pageSize));
            all.addAll(page.content());
            if ((long) current * pageSize >= page.total()) {
                break;
            }
            current++;
        }
        return all;
    }
}
