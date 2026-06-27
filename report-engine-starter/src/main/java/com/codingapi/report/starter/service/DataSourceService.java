package com.codingapi.report.starter.service;

import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.data.dataset.DataType;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.datasource.ColumnMeta;
import com.codingapi.report.data.datasource.DataExtractor;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.IntrospectedTable;
import com.codingapi.report.data.datasource.RawTable;
import com.codingapi.report.data.datasource.TestResult;
import com.codingapi.report.data.datasource.credential.CredentialService;
import com.codingapi.report.data.datasource.type.DataSourceType;
import com.codingapi.report.data.datasource.type.DbDataSourceType;
import com.codingapi.report.dto.datamodel.DataSourceDTO;
import com.codingapi.report.dto.datamodel.DatasetDTO;
import com.codingapi.report.dto.datamodel.FieldDTO;
import com.codingapi.report.repository.DataSourceRepository;
import com.codingapi.report.repository.DataSourceTypeRepository;
import com.codingapi.report.repository.PageQuery;
import com.codingapi.report.repository.PageResult;
import com.codingapi.report.starter.dto.DatasetDtos.PreviewDTO;
import com.codingapi.report.starter.properties.ReportProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据源（连接）操作业务：CRUD + 连接测试 + 表/列探查 + 数据集预览。
 *
 * <p>CRUD 走 {@link DataSourceRepository}（以领域 {@link DataSource} 存取），出入站用 {@link DataSourceDTO}：
 * 敏感字段仅在出口 {@link #getMasked} 脱敏；保存时 {@code ***} 占位用旧值回填。 DB 类型在保存/测试前由 {@link DriverLoader}
 * 注册外部驱动， 使后续 {@code DriverManager.getConnection} 能识别外部 jar 加载的驱动。
 *
 * <p>提取器按 {@code supports(type)} 在 {@code List<DataExtractor>} 中派发，与渲染链路同一注册表范式。 预览/探查 的连接从 {@link
 * DataModelService#loadDataModel} 取（已解密）。
 */
@Slf4j
public class DataSourceService {

    private final DataModelService dataModelService;
    private final List<DataExtractor> extractors;
    private final DataSourceRepository repository;
    private final CredentialService credentials;
    private final DriverLoader driverLoader;
    private final DataSourceTypeRepository typeRepository;
    private final ReportProperties properties;

    public DataSourceService(
            DataModelService dataModelService,
            List<DataExtractor> extractors,
            DataSourceRepository repository,
            CredentialService credentials,
            DriverLoader driverLoader,
            DataSourceTypeRepository typeRepository,
            ReportProperties properties) {
        this.dataModelService = dataModelService;
        this.extractors = extractors;
        this.repository = repository;
        this.credentials = credentials;
        this.driverLoader = driverLoader;
        this.typeRepository = typeRepository;
        this.properties = properties;
    }

    // ============================================================
    // CRUD
    // ============================================================

    public PageResult<DataSource> page(int current, int pageSize) {
        return repository.page(new PageQuery(current, pageSize));
    }

    /** 详情（{@code config} 脱敏）。不存在返回 null。 */
    public DataSourceDTO getMasked(String id) {
        DataSource ds = repository.find(id);
        return ds != null ? toDtoMasked(ds) : null;
    }

    /** 新建/更新：{@code ***} 凭证回填旧值（明文存储，落盘加密交仓库实现）；DB 类型触发驱动注册。 */
    public String save(DataSourceDTO dto) {
        DataSource incoming = fromDto(dto);
        DataSource old = dto.id() != null && !dto.id().isBlank() ? repository.find(dto.id()) : null;
        mergeMaskedCredentials(incoming, old);
        // DTO 不带 datasets，更新时保留 introspect 阶段已解析持久化的数据集，避免被清空
        if ((incoming.getDatasets() == null || incoming.getDatasets().isEmpty())
                && old != null
                && old.getDatasets() != null) {
            incoming.setDatasets(old.getDatasets());
        }
        String id = repository.save(incoming);
        registerDriverIfNeeded(incoming);
        return id;
    }

    public void delete(String id) {
        repository.delete(id);
    }

    // ============================================================
    // 连接测试 / 探查 / 预览（既有链路保留）
    // ============================================================

    /** 测试连接（不落库，凭证明文）。DB 类型先注册外部驱动。 */
    public TestResult testConnection(DataSourceDTO dto) {
        DataSource ds = fromDto(dto);
        registerDriverIfNeeded(ds);
        return findExtractor(ds.getType()).test(ds);
    }

    /**
     * 元数据探查：按已保存的连接 id 解析所有可用表/sheet 及列定义。
     *
     * <p>从仓库取出的 {@code config} 若含 {@code enc:} 加密值（落盘加密由仓库实现）， 先用 {@link
     * CredentialService#decryptConfig} 解密；DB 类型还会触发外部驱动注册。 之后派发到对应 {@link DataExtractor#introspect}。
     */
    public List<IntrospectedTable> introspect(String id) {
        DataSource ds = repository.find(id);
        if (ds == null) {
            throw new IllegalArgumentException("数据源不存在: " + id);
        }
        if (ds.getConfig() != null && credentials != null) {
            ds.setConfig(credentials.decryptConfig(ds.getConfig()));
        }
        registerDriverIfNeeded(ds);
        return mergeSavedAliases(ds, toBusinessTypes(findExtractor(ds.getType()).introspect(ds)));
    }

    /** 探查列的原生类型统一映射成报表业务 {@link DataType} 名（前端/数据集只认业务类型），并透传字段备注/表别名。 */
    private static List<IntrospectedTable> toBusinessTypes(List<IntrospectedTable> tables) {
        return tables.stream()
                .map(
                        t ->
                                new IntrospectedTable(
                                        t.name(),
                                        t.columns().stream()
                                                .map(
                                                        c ->
                                                                new ColumnMeta(
                                                                        c.name(),
                                                                        mapDataType(c.dataType())
                                                                                .name(),
                                                                        c.primaryKey(),
                                                                        c.remark()))
                                                .toList(),
                                        t.alias()))
                .toList();
    }

    /**
     * 把数据源下已保存的数据集别名按表名回填到探查结果，供数据模型管理添加数据集时展示「别名（表名）」。
     *
     * <p>探查层（extractor）只知物理表名，不知用户在数据源管理里维护的别名；此处从聚合根已持有的 {@link
     * TableDataset} 取 alias 按 {@code sourceTable == table.name} 匹配回填。重新解析时前端 {@code mergeTables} 仍以用户已编辑的别名为准，不会被覆盖。
     */
    private static List<IntrospectedTable> mergeSavedAliases(
            DataSource ds, List<IntrospectedTable> tables) {
        if (ds.getDatasets() == null || ds.getDatasets().isEmpty()) return tables;
        Map<String, String> aliasByTable = new LinkedHashMap<>();
        for (Dataset d : ds.getDatasets()) {
            if (d instanceof TableDataset t && t.getSourceTable() != null) {
                aliasByTable.put(t.getSourceTable(), t.getAlias());
            }
        }
        if (aliasByTable.isEmpty()) return tables;
        return tables.stream()
                .map(
                        t -> {
                            String saved = aliasByTable.get(t.name());
                            return saved != null
                                    ? new IntrospectedTable(t.name(), t.columns(), saved)
                                    : t;
                        })
                .toList();
    }

    /** 原生类型名 / 业务类型名 → 业务 {@link DataType} 粗映射，无法识别回退 STRING。 */
    private static DataType mapDataType(String nativeType) {
        if (nativeType == null) return DataType.STRING;
        String t = nativeType.toUpperCase();
        if (t.contains("TIMESTAMP") || t.contains("DATETIME")) return DataType.DATETIME;
        if (t.contains("DATE")) return DataType.DATE;
        if (t.contains("BOOL")) return DataType.BOOLEAN;
        if (t.contains("JSON")) return DataType.JSON;
        if (t.contains("INT")
                || t.contains("NUM")
                || t.contains("DEC")
                || t.contains("DOUBLE")
                || t.contains("FLOAT")
                || t.contains("REAL")) return DataType.NUMBER;
        return DataType.STRING;
    }

    /**
     * 上传 Excel/CSV 文件到 {@link ReportProperties} 配置的目录，并返回解析后的表/列元数据。
     *
     * <p>EXCEL 落到 {@code properties.excel.dir}，CSV 落到 {@code properties.csv.dir}。 文件名做安全清洗（去路径分隔符 +
     * 防 path traversal）。返回的 {@link UploadResult} 含保存路径与解析出的表列表， 前端可据此构造 {@link DataSourceDTO}
     * 持久化（{@code config.path = savedPath}）。
     *
     * @param file 上传的文件
     * @param type {@code EXCEL} 或 {@code CSV}；为空时按文件扩展名推断
     */
    public UploadResult uploadAndIntrospect(MultipartFile file, String type) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("上传文件为空");
        }
        String kind = (type == null || type.isBlank()) ? detectKind(file.getOriginalFilename()) : type.toUpperCase();
        String dir =
                switch (kind) {
                    case "EXCEL" -> properties.getExcel().getDir();
                    case "CSV" -> properties.getCsv().getDir();
                    default -> throw new IOException("不支持的上传类型: " + kind);
                };
        Path dirPath = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(dirPath);
        String filename = sanitizeFilename(file.getOriginalFilename());
        Path target = dirPath.resolve(filename).normalize();
        if (!target.startsWith(dirPath)) {
            throw new IOException("非法文件名: " + filename);
        }
        try (var in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        String savedPath = target.toString();
        DataSource ds =
                DataSource.builder()
                        .name(filename)
                        .type(DataSourceType.of(kind, Map.of("path", savedPath)))
                        .config(Map.of("path", savedPath))
                        .build();
        List<IntrospectedTable> tables = toBusinessTypes(findExtractor(ds.getType()).introspect(ds));
        return new UploadResult(savedPath, kind, tables);
    }

    private static String detectKind(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("无法推断上传类型：文件名为空");
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".csv")) return "CSV";
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) return "EXCEL";
        throw new IllegalArgumentException("无法推断上传类型（.csv/.xlsx/.xls）: " + filename);
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) return "upload";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) return "upload";
        return name;
    }

    /** 上传结果：保存路径 + 类型 + 解析出的表/列元数据。 */
    public record UploadResult(String savedPath, String type, List<IntrospectedTable> tables) {}

    public List<String> listTables(String dataModelId, String datasourceId) {
        DataSource ds = loadDataSource(dataModelId, datasourceId);
        return findExtractor(ds.getType()).listTables(ds);
    }

    public List<ColumnMeta> listColumns(String dataModelId, String datasourceId, String table) {
        DataSource ds = loadDataSource(dataModelId, datasourceId);
        return findExtractor(ds.getType()).listColumns(ds, table);
    }

    /** 数据集预览：提取前 N 行，去掉列名 datasetId 前缀。 */
    public PreviewDTO previewDataset(String dataModelId, String datasetId, int limit) {
        DataModel dm = dataModelService.loadDataModel(dataModelId);
        Dataset ds =
                dm.getDatasets().stream()
                        .filter(d -> d.getId().equals(datasetId))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("数据集不存在: " + datasetId));
        if (!(ds instanceof TableDataset tds)) {
            throw new IllegalArgumentException("非表格数据集: " + datasetId);
        }
        DataSource source = tds.getDatasource();
        if (source == null) {
            throw new IllegalArgumentException("数据集未绑定数据源: " + tds.getDatasourceId());
        }
        RawTable raw = findExtractor(source.getType()).extract(source, tds);

        String prefix = tds.getId() + ".";
        List<String> columns =
                raw.getColumns().stream()
                        .map(c -> c.startsWith(prefix) ? c.substring(prefix.length()) : c)
                        .toList();
        List<Map<String, Object>> rows =
                raw.getRows().stream()
                        .limit(limit)
                        .map(
                                row -> {
                                    Map<String, Object> simplified = new LinkedHashMap<>();
                                    for (Map.Entry<String, Object> e : row.entrySet()) {
                                        String key = e.getKey();
                                        simplified.put(
                                                key.startsWith(prefix)
                                                        ? key.substring(prefix.length())
                                                        : key,
                                                e.getValue());
                                    }
                                    return simplified;
                                })
                        .toList();
        return new PreviewDTO(columns, rows);
    }

    // ============================================================
    // 内部
    // ============================================================

    private DataSource fromDto(DataSourceDTO dto) {
        DataSourceType type = DataSourceType.of(dto.type(), dto.config());
        DataSource ds =
                DataSource.builder()
                        .id(dto.id())
                        .name(dto.name())
                        .type(type)
                        .typeConfigId(dto.typeConfigId())
                        .config(dto.config())
                        .build();
        if (dto.datasets() != null) {
            ds.setDatasets(dto.datasets().stream().map(d -> toTableDataset(d, ds)).toList());
        }
        return ds;
    }

    /** DTO → 领域 {@link TableDataset}，回填所属 {@link DataSource}（取数时无需再按 id 查找）。 */
    private static Dataset toTableDataset(DatasetDTO d, DataSource owner) {
        List<Field> fields =
                d.fields() == null
                        ? List.of()
                        : d.fields().stream()
                                .map(
                                        f ->
                                                Field.builder()
                                                        .name(f.name())
                                                        .alias(f.alias())
                                                        .dataType(mapDataType(f.dataType()))
                                                        .primaryKey(f.primaryKey())
                                                        .build())
                                .toList();
        return TableDataset.builder()
                .id(
                        d.id() != null && !d.id().isBlank()
                                ? d.id()
                                : owner.getId() + "::" + d.sourceTable())
                .datasource(owner)
                .datasourceId(owner.getId())
                .sourceTable(d.sourceTable())
                .alias(d.alias() != null ? d.alias() : d.sourceTable())
                .fields(fields)
                .build();
    }

    private DataSourceDTO toDtoMasked(DataSource ds) {
        return new DataSourceDTO(
                ds.getId(),
                ds.getName(),
                ds.getType() != null ? ds.getType().type() : null,
                ds.getTypeConfigId(),
                credentials.maskConfig(ds.getConfig()),
                toDatasetDtos(ds.getDatasets()));
    }

    /** 领域数据集 → DTO（仅物理表数据集；字段类型枚举名输出）。 */
    private static List<DatasetDTO> toDatasetDtos(List<Dataset> datasets) {
        if (datasets == null) return null;
        List<DatasetDTO> out = new ArrayList<>();
        for (Dataset d : datasets) {
            if (!(d instanceof TableDataset t)) continue;
            List<FieldDTO> fields =
                    t.getFields() == null
                            ? List.of()
                            : t.getFields().stream()
                                    .map(
                                            f ->
                                                    new FieldDTO(
                                                            f.getName(),
                                                            f.getAlias(),
                                                            f.getDataType() != null
                                                                    ? f.getDataType().name()
                                                                    : null,
                                                            f.isPrimaryKey()))
                                    .toList();
            out.add(
                    new DatasetDTO(
                            t.getId(),
                            t.getName(),
                            t.getAlias(),
                            "TABLE",
                            t.getDatasourceId(),
                            t.getSourceTable(),
                            fields,
                            null));
        }
        return out;
    }

    /** 前端回传的 {@code ***} 占位用旧连接的真实值回填，避免覆盖真实凭证。 */
    private void mergeMaskedCredentials(DataSource incoming, DataSource old) {
        if (old == null
                || incoming.getConfig() == null
                || old.getConfig() == null
                || credentials == null) {
            return;
        }
        Map<String, Object> merged = new LinkedHashMap<>(incoming.getConfig());
        boolean changed = false;
        for (Map.Entry<String, Object> e : merged.entrySet()) {
            if (credentials.isMasked(e.getValue())) {
                Object oldVal = old.getConfig().get(e.getKey());
                if (oldVal != null) {
                    e.setValue(oldVal);
                    changed = true;
                }
            }
        }
        if (changed) incoming.setConfig(merged);
    }

    /** DB 类型按 {@code typeConfigId} 找到类型配置，注册外部驱动 jar 到 DriverManager。 */
    private void registerDriverIfNeeded(DataSource ds) {
        if (driverLoader == null || typeRepository == null) return;
        if (!(ds.getType() instanceof DbDataSourceType)) return;
        String configId = ds.getTypeConfigId();
        if (configId == null || configId.isBlank()) return;
        var cfg = typeRepository.find(configId);
        if (cfg == null || !(cfg.getType() instanceof DbDataSourceType db)) return;
        try {
            driverLoader.registerDriver(db.jarFile(), db.driverClass());
        } catch (IOException e) {
            log.warn("skip driver registration for typeConfigId={}: {}", configId, e.getMessage());
        }
    }

    private DataExtractor findExtractor(DataSourceType type) {
        return extractors.stream()
                .filter(e -> e.supports(type.type()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("无提取器支持类型: " + type.type()));
    }

    private DataSource loadDataSource(String dataModelId, String datasourceId) {
        DataModel dm = dataModelService.loadDataModel(dataModelId);
        return dm.getDatasets().stream()
                .filter(
                        d ->
                                d instanceof TableDataset t
                                        && t.getDatasource() != null
                                        && datasourceId.equals(t.getDatasource().getId()))
                .map(d -> ((TableDataset) d).getDatasource())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("连接不存在: " + datasourceId));
    }
}
