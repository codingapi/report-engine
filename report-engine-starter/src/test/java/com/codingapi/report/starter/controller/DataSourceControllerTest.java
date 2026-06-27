package com.codingapi.report.starter.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.codingapi.report.data.datasource.DataExtractor;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.IntrospectedTable;
import com.codingapi.report.data.datasource.credential.CredentialService;
import com.codingapi.report.data.datasource.extractor.CsvDataExtractor;
import com.codingapi.report.data.datasource.extractor.ExcelDataExtractor;
import com.codingapi.report.dto.datamodel.DataSourceDTO;
import com.codingapi.report.excel.ExcelExporter;
import com.codingapi.report.excel.pojo.Cell;
import com.codingapi.report.excel.pojo.Sheet;
import com.codingapi.report.excel.pojo.Workbook;
import com.codingapi.report.repository.DataSourceRepository;
import com.codingapi.report.repository.DataSourceTypeRepository;
import com.codingapi.report.repository.PageQuery;
import com.codingapi.report.repository.PageResult;
import com.codingapi.report.starter.controller.DataSourceController.DataSourceBrief;
import com.codingapi.report.starter.properties.ReportProperties;
import com.codingapi.report.starter.service.DataSourceService;
import com.codingapi.report.starter.service.DataSourceService.UploadResult;
import com.codingapi.report.starter.service.DriverLoader;
import com.codingapi.springboot.framework.dto.response.MultiResponse;
import com.codingapi.springboot.framework.dto.response.SingleResponse;
import com.fasterxml.jackson.databind.node.TextNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 前置条件：CRUD 与脱敏/回填走 {@link DataSourceService}；外部驱动注册在 {@link DriverLoader} 的单测里覆盖， 这里用 mock 的
 * DriverLoader（仅校验调用与否），避免重复造 jar 加载链路。
 */
class DataSourceControllerTest {

    private FakeDataSourceRepository repository;
    private CredentialService credentials;
    private DriverLoader driverLoader;
    private DataSourceTypeRepository typeRepository;
    private ReportProperties properties;
    private DataSourceService service;
    private DataSourceController controller;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        repository = new FakeDataSourceRepository();
        // 真实的 CredentialService（默认 key），覆盖 maskConfig / isMasked / 解密链路。
        credentials = new CredentialService("test-key");
        driverLoader = mock(DriverLoader.class);
        typeRepository = mock(DataSourceTypeRepository.class);
        // DataExtractor 列表给 CSV + EXCEL 提取器，覆盖 introspect/upload 链路。
        List<DataExtractor> extractors = List.of(new CsvDataExtractor(), new ExcelDataExtractor());
        // DataModelService 仅在 listTables/columns/preview 链路用，CRUD 测试不需要。
        var dataModelService = mock(com.codingapi.report.starter.service.DataModelService.class);
        properties = new ReportProperties();
        properties.getExcel().setDir(tempDir.resolve("excel").toString());
        properties.getCsv().setDir(tempDir.resolve("csv").toString());
        service =
                new DataSourceService(
                        dataModelService,
                        extractors,
                        repository,
                        credentials,
                        driverLoader,
                        typeRepository,
                        properties);
        controller = new DataSourceController(service);
    }

    @Test
    void saveAndGetMappingRoundTrip() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("path", "data/employees.csv");
        DataSourceDTO dto = new DataSourceDTO(null, "员工 CSV", "CSV", null, config);

        SingleResponse<String> saveResp = controller.save(dto);
        assertTrue(saveResp.isSuccess());
        String id = saveResp.getData();
        assertNotNull(id);

        SingleResponse<DataSourceDTO> getResp = controller.get(id);
        assertTrue(getResp.isSuccess());
        DataSourceDTO loaded = getResp.getData();
        assertNotNull(loaded);
        assertEquals("员工 CSV", loaded.name());
        assertEquals("CSV", loaded.type());
        assertEquals("data/employees.csv", loaded.config().get("path"));
    }

    @Test
    void saveUpdatePreservesCreateTime() throws Exception {
        Map<String, Object> config = Map.of("path", "data/x.csv");
        String id = controller.save(new DataSourceDTO(null, "x", "CSV", null, config)).getData();

        Thread.sleep(5);

        controller.save(new DataSourceDTO(id, "x-updated", "CSV", null, config));

        DataSourceDTO loaded = controller.get(id).getData();
        assertEquals("x-updated", loaded.name());
        DataSource raw = repository.find(id);
        assertTrue(raw.getUpdateTime() >= raw.getCreateTime());
    }

    @Test
    void listReturnsBriefsPaginated() {
        for (int i = 0; i < 12; i++) {
            controller.save(
                    new DataSourceDTO(
                            null, "ds-" + i, "CSV", null, Map.of("path", "data/" + i + ".csv")));
        }

        MultiResponse<DataSourceBrief> page1 = controller.list(1, 10);
        assertTrue(page1.isSuccess());
        assertEquals(12, page1.getData().getTotal());
        assertEquals(10, page1.getData().getList().size());

        MultiResponse<DataSourceBrief> page2 = controller.list(2, 10);
        assertEquals(2, page2.getData().getList().size());

        DataSourceBrief brief = page1.getData().getList().iterator().next();
        assertNotNull(brief.id());
        assertNotNull(brief.name());
        assertEquals("CSV", brief.type());
        assertTrue(brief.createTime() > 0);
        // brief 不暴露 config
        assertNull(brief.typeConfigId());
    }

    @Test
    void listUsesDefaultsForMissingParams() {
        controller.save(new DataSourceDTO(null, "solo", "CSV", null, Map.of("path", "x.csv")));
        MultiResponse<DataSourceBrief> resp = controller.list(1, 10);
        assertEquals(1, resp.getData().getTotal());
    }

    @Test
    void getMissingReturnsNullData() {
        SingleResponse<DataSourceDTO> resp = controller.get("nope");
        assertTrue(resp.isSuccess());
        assertNull(resp.getData());
    }

    @Test
    void deleteRemovesRecord() {
        String id =
                controller
                        .save(new DataSourceDTO(null, "x", "CSV", null, Map.of("path", "x.csv")))
                        .getData();
        assertNotNull(controller.get(id).getData());

        SingleResponse<Void> del = controller.delete(id);
        assertTrue(del.isSuccess());
        assertNull(controller.get(id).getData());
    }

    @Test
    void saveMergesMaskedPasswordFromOld() {
        // 首次保存：明文密码（service 内存层不加密，落盘加密交仓库实现）。
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("url", "jdbc:x://db");
        cfg.put("username", "admin");
        cfg.put("password", "secret123");
        String id = controller.save(new DataSourceDTO(null, "db", "CSV", null, cfg)).getData();

        // 出口态：password 被脱敏成 ***
        DataSourceDTO masked = controller.get(id).getData();
        assertEquals("***", masked.config().get("password"));
        assertEquals("admin", masked.config().get("username"));

        // 前端回传 *** 占位 + 改了 username；password 应回填旧值 secret123，不被 *** 覆盖
        Map<String, Object> updateCfg = new LinkedHashMap<>();
        updateCfg.put("url", "jdbc:x://db");
        updateCfg.put("username", "admin2");
        updateCfg.put("password", "***");
        controller.save(new DataSourceDTO(id, "db-updated", "CSV", null, updateCfg));

        DataSource raw = repository.find(id);
        assertEquals("admin2", raw.getConfig().get("username"));
        assertEquals("secret123", raw.getConfig().get("password"), "*** 应回填为旧值");
    }

    @Test
    void testConnectionDoesNotPersistAndPropagatesExtractorError() {
        // CSV 提取器默认不支持连接测试（DataExtractor.test 默认抛 UnsupportedOperationException）；
        // 这里只验证测试不落库、且异常按预期透出。
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("path", "data/employees.csv");
        DataSourceDTO dto = new DataSourceDTO(null, "tmp", "CSV", null, config);

        assertThrows(UnsupportedOperationException.class, () -> controller.test(dto));
        assertEquals(0, repository.store.size(), "test 不应落库");
    }

    @Test
    void csvSaveDoesNotInvokeDriverLoader() {
        controller.save(new DataSourceDTO(null, "csv", "CSV", null, Map.of("path", "x.csv")));
        verifyNoInteractions(driverLoader);
    }

    @Test
    void introspectCsvFilesystemPathReturnsHeaderColumns(@TempDir Path tempDir) throws Exception {
        Path csv = tempDir.resolve("staff.csv");
        Files.writeString(csv, "id,name,age\n1,alice,30\n2,bob,25");

        Map<String, Object> config = Map.of("path", csv.toString());
        String id = controller.save(new DataSourceDTO(null, "csv", "CSV", null, config)).getData();

        MultiResponse<IntrospectedTable> resp = controller.introspect(id);
        assertTrue(resp.isSuccess());
        List<IntrospectedTable> tables = new java.util.ArrayList<>(resp.getData().getList());
        assertEquals(1, tables.size(), "CSV 探查应返回单张表");
        IntrospectedTable table = tables.get(0);
        assertEquals("staff", table.name());
        List<String> colNames = table.columns().stream().map(c -> c.name()).toList();
        assertEquals(List.of("id", "name", "age"), colNames);
        assertTrue(
                table.columns().stream()
                        .allMatch(c -> "STRING".equals(c.dataType()) && !c.primaryKey()));
    }

    @Test
    void introspectMissingDataSourceThrows() {
        assertThrows(IllegalArgumentException.class, () -> controller.introspect("nope"));
    }

    @Test
    void uploadCsvFileSavesAndIntrospects() throws Exception {
        byte[] bytes = "code,label\nA,Apple\nB,Banana".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "fruits.csv", "text/csv", bytes);

        SingleResponse<UploadResult> resp = controller.upload(file, "CSV");
        assertTrue(resp.isSuccess());
        UploadResult result = resp.getData();
        assertNotNull(result);
        assertEquals("CSV", result.type());
        assertTrue(Files.exists(Path.of(result.savedPath())), "上传文件应落盘");
        assertEquals("fruits.csv", Path.of(result.savedPath()).getFileName().toString());
        assertEquals(1, result.tables().size());
        IntrospectedTable table = result.tables().get(0);
        assertEquals("fruits", table.name());
        assertEquals(
                List.of("code", "label"), table.columns().stream().map(c -> c.name()).toList());
    }

    @Test
    void uploadExcelFileSavesAndIntrospectsAllSheets() throws Exception {
        Workbook wb = buildWorkbookWithSheets(List.of("Staff", "Depts"));
        byte[] bytes = new ExcelExporter().export(wb);
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "book.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        bytes);

        SingleResponse<UploadResult> resp = controller.upload(file, "EXCEL");
        assertTrue(resp.isSuccess());
        UploadResult result = resp.getData();
        assertEquals("EXCEL", result.type());
        assertTrue(Files.exists(Path.of(result.savedPath())));
        assertEquals(2, result.tables().size(), "应解析出两个 sheet");
        assertEquals(
                List.of("Staff", "Depts"),
                result.tables().stream().map(IntrospectedTable::name).toList());
        // 每个 sheet 表头应有 id/name 两列
        for (IntrospectedTable t : result.tables()) {
            assertEquals(List.of("id", "name"), t.columns().stream().map(c -> c.name()).toList());
        }
    }

    @Test
    void uploadInfersTypeFromExtensionWhenTypeParamMissing() throws Exception {
        byte[] bytes = "x,y\n1,2".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "auto.csv", "text/csv", bytes);
        SingleResponse<UploadResult> resp = controller.upload(file, null);
        assertEquals("CSV", resp.getData().type());
    }

    @Test
    void uploadRejectsUnknownExtension() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "data.bin", "application/octet-stream", new byte[] {1});
        assertThrows(IllegalArgumentException.class, () -> controller.upload(file, null));
    }

    private static Workbook buildWorkbookWithSheets(List<String> sheetNames) {
        Workbook wb = new Workbook();
        List<Sheet> sheets = new ArrayList<>();
        for (String name : sheetNames) {
            Sheet sheet = new Sheet();
            sheet.setId(UUID.randomUUID().toString());
            sheet.setName(name);
            List<Cell> cells = new ArrayList<>();
            cells.add(cellOf(0, 0, "id"));
            cells.add(cellOf(0, 1, "name"));
            sheet.setCells(cells);
            sheets.add(sheet);
        }
        wb.setSheets(sheets);
        return wb;
    }

    private static Cell cellOf(int row, int col, String text) {
        Cell c = new Cell();
        c.setRow(row);
        c.setCol(col);
        c.setValue(TextNode.valueOf(text));
        return c;
    }

    private static class FakeDataSourceRepository implements DataSourceRepository {
        final ConcurrentHashMap<String, DataSource> store = new ConcurrentHashMap<>();

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
            List<DataSource> all = new ArrayList<>(store.values());
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
