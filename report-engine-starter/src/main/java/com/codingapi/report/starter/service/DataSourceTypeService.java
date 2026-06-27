package com.codingapi.report.starter.service;

import com.codingapi.report.data.datasource.DataSourceTypeConfig;
import com.codingapi.report.dto.datasource.DataSourceTypeDTO;
import com.codingapi.report.repository.DataSourceTypeRepository;
import com.codingapi.report.repository.PageQuery;
import com.codingapi.report.repository.PageResult;
import com.codingapi.report.starter.properties.ReportProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.web.multipart.MultipartFile;

public class DataSourceTypeService {

    private final DataSourceTypeRepository repository;
    private final ReportProperties properties;
    private final DriverJarScanner driverJarScanner;

    public DataSourceTypeService(DataSourceTypeRepository repository, ReportProperties properties) {
        this.repository = repository;
        this.properties = properties;
        this.driverJarScanner = new DriverJarScanner();
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

    /** 上传驱动 jar 到 {@code ReportProperties.driver.dir}，返回解析到的驱动类名列表。 */
    public DriverJarScanner.DriverJarScanResult uploadDriverJar(MultipartFile file)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("driver jar file is empty");
        }
        Path driverDir = Path.of(properties.getDriver().getDir());
        Files.createDirectories(driverDir);

        String filename = sanitizeFilename(file.getOriginalFilename());
        Path target = driverDir.resolve(filename).normalize();
        if (!target.startsWith(driverDir.normalize())) {
            throw new IOException("invalid jar filename: " + filename);
        }
        try (var in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return driverJarScanner.scan(target);
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "driver.jar";
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            return "driver.jar";
        }
        return name;
    }
}
