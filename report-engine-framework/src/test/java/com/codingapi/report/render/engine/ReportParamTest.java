package com.codingapi.report.render.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.data.dataset.DataType;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.DataSourceType;
import com.codingapi.report.data.datasource.csv.CsvDataExtractor;
import com.codingapi.report.excel.ExcelExporter;
import com.codingapi.report.excel.ExcelImporter;
import com.codingapi.report.excel.pojo.Cell;
import com.codingapi.report.excel.pojo.Sheet;
import com.codingapi.report.excel.pojo.Workbook;
import com.codingapi.report.expression.Templates;
import com.codingapi.report.expression.Value;
import com.codingapi.report.operator.condition.CompareOperator;
import com.codingapi.report.operator.condition.Condition;
import com.codingapi.report.param.ParamContext;
import com.codingapi.report.param.ParamSource;
import com.codingapi.report.param.Parameter;
import com.codingapi.report.render.Report;
import com.codingapi.report.render.grid.CellBinding;
import com.codingapi.report.render.grid.CellRef;
import com.codingapi.report.render.grid.ExpandMode;
import com.codingapi.report.render.grid.Expansion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 报表参数场景的全链路验证：配置 → 渲染 → <b>导出到本地 xlsx 文件</b> → 回读断言。
 *
 * <ol>
 *   <li>{@link #paramDisplay()} 参数直接显示 + 文本插值 + NameRef
 *   <li>{@link #paramFilter()} 参数作为过滤条件
 *   <li>{@link #paramMissing()} 参数未传入时的行为基线
 * </ol>
 *
 * 文件输出到 {@code target/reports/param-*.xlsx}。
 */
class ReportParamTest {

    private final ReportRenderer renderer = new ReportRenderer(List.of(new CsvDataExtractor()));

    // ============================================================
    // 1. 参数直接显示 + 文本插值
    // ============================================================

    @Test
    @DisplayName("参数显示：ParamValue / NameRef / Template 三种方式引用参数 → xlsx")
    void paramDisplay() throws Exception {
        // 数据模型（用一个简单的 products 数据集做背景）
        DataSource src = csv("ds_prod", "/data/products.csv");
        Dataset prod =
                TableDataset.builder()
                        .id("d_prod")
                        .datasourceId("ds_prod")
                        .sourceTable("products.csv")
                        .fields(
                                List.of(
                                        Field.builder()
                                                .name("name")
                                                .dataType(DataType.STRING)
                                                .build(),
                                        Field.builder()
                                                .name("price")
                                                .dataType(DataType.NUMBER)
                                                .build()))
                        .build();
        DataModel dm =
                DataModel.builder()
                        .id("dm_param_display")
                        .name("参数显示测试")
                        .datasources(List.of(src))
                        .datasets(List.of(prod))
                        .relationships(List.of())
                        .build();

        // 参数定义
        Parameter companyName =
                Parameter.builder()
                        .name("companyName")
                        .alias("公司名称")
                        .dataType(DataType.STRING)
                        .source(new ParamSource.External(true, null))
                        .build();
        Parameter reportYear =
                Parameter.builder()
                        .name("reportYear")
                        .alias("报表年份")
                        .dataType(DataType.STRING)
                        .source(new ParamSource.External(true, null))
                        .build();

        // 单元格绑定
        // row0: "${companyName}${reportYear}年度员工报表"（文本插值）
        CellBinding title =
                CellBinding.builder()
                        .cell(new CellRef("sheet1", 0, 0))
                        .value(Templates.parse("${companyName}${reportYear}年度员工报表"))
                        .build();
        // row1,col0: 直接显示参数值（ParamValue）
        CellBinding paramCell =
                CellBinding.builder()
                        .cell(new CellRef("sheet1", 1, 0))
                        .value(new Value.ParamValue("companyName"))
                        .build();
        // row1,col1: NameRef 方式引用参数
        CellBinding nameRefCell =
                CellBinding.builder()
                        .cell(new CellRef("sheet1", 1, 1))
                        .value(new Value.NameRef("reportYear"))
                        .build();

        Report report =
                Report.builder()
                        .id("r_param_display")
                        .name("参数显示测试")
                        .dataModelId(dm.getId())
                        .templateId("tpl_param_display")
                        .parameters(List.of(companyName, reportYear))
                        .cellBindings(List.of(title, paramCell, nameRefCell))
                        .loopBlocks(List.of())
                        .summaries(List.of())
                        .build();

        Sheet sheet =
                run(
                        dm,
                        report,
                        Map.of("companyName", "测试科技", "reportYear", "2024"),
                        "param-display");

        // 断言
        assertEquals("测试科技2024年度员工报表", text(sheet, 0, 0));
        assertEquals("测试科技", text(sheet, 1, 0));
        assertEquals("2024", text(sheet, 1, 1));
    }

    // ============================================================
    // 2. 参数作为过滤条件
    // ============================================================

    @Test
    @DisplayName("参数过滤：按 empStatus 参数筛选员工 → xlsx")
    void paramFilter() throws Exception {
        DataSource src = csv("ds_emp", "/data/employees.csv");
        Dataset emp =
                TableDataset.builder()
                        .id("d_emp")
                        .datasourceId("ds_emp")
                        .sourceTable("employees.csv")
                        .fields(
                                List.of(
                                        Field.builder()
                                                .name("id")
                                                .dataType(DataType.NUMBER)
                                                .build(),
                                        Field.builder()
                                                .name("name")
                                                .dataType(DataType.STRING)
                                                .build(),
                                        Field.builder()
                                                .name("status")
                                                .dataType(DataType.STRING)
                                                .build()))
                        .build();
        DataModel dm =
                DataModel.builder()
                        .id("dm_param_filter")
                        .name("参数过滤测试")
                        .datasources(List.of(src))
                        .datasets(List.of(emp))
                        .relationships(List.of())
                        .build();

        // 参数定义
        Parameter empStatus =
                Parameter.builder()
                        .name("empStatus")
                        .alias("员工状态")
                        .dataType(DataType.STRING)
                        .source(new ParamSource.External(true, null))
                        .build();

        // 单元格绑定
        // row0: "${empStatus}员工列表"（标题插值）
        CellBinding title =
                CellBinding.builder()
                        .cell(new CellRef("sheet1", 0, 0))
                        .value(Templates.parse("${empStatus}员工列表"))
                        .build();
        // row1: 表头
        CellBinding header = label(1, 0, "姓名");
        // row2+: 姓名列表（带过滤条件 status = empStatus）
        CellBinding nameCol =
                CellBinding.builder()
                        .cell(new CellRef("sheet1", 2, 0))
                        .value(new Value.FieldValue(new FieldRef("d_emp", "name")))
                        .expansion(Expansion.VERTICAL)
                        .expandMode(ExpandMode.LIST)
                        .conditions(
                                List.of(
                                        Condition.builder()
                                                .left(
                                                        new Value.FieldValue(
                                                                new FieldRef("d_emp", "status")))
                                                .operator(CompareOperator.EQ)
                                                .right(new Value.ParamValue("empStatus"))
                                                .build()))
                        .build();

        Report report =
                Report.builder()
                        .id("r_param_filter")
                        .name("参数过滤测试")
                        .dataModelId(dm.getId())
                        .templateId("tpl_param_filter")
                        .parameters(List.of(empStatus))
                        .cellBindings(List.of(title, header, nameCol))
                        .loopBlocks(List.of())
                        .summaries(List.of())
                        .build();

        // 传入 empStatus = "在职" → 只有张三、李四（排除王五"离职"）
        Sheet sheet = run(dm, report, Map.of("empStatus", "在职"), "param-filter");

        // 断言
        assertEquals("在职员工列表", text(sheet, 0, 0));
        assertEquals("姓名", text(sheet, 1, 0));
        assertEquals("张三", text(sheet, 2, 0));
        assertEquals("李四", text(sheet, 3, 0));
        // 王五（离职）不应出现：row4 应该为空
        assertNull(findCell(sheet, 4, 0), "离职员工不应出现在过滤结果中");
    }

    // ============================================================
    // 3. 参数未传入时的行为基线
    // ============================================================

    @Test
    @DisplayName("参数缺失：未传入参数时渲染不报错，值为空 → xlsx")
    void paramMissing() throws Exception {
        DataSource src = csv("ds_prod", "/data/products.csv");
        Dataset prod =
                TableDataset.builder()
                        .id("d_prod")
                        .datasourceId("ds_prod")
                        .sourceTable("products.csv")
                        .fields(
                                List.of(
                                        Field.builder()
                                                .name("name")
                                                .dataType(DataType.STRING)
                                                .build(),
                                        Field.builder()
                                                .name("price")
                                                .dataType(DataType.NUMBER)
                                                .build()))
                        .build();
        DataModel dm =
                DataModel.builder()
                        .id("dm_param_missing")
                        .name("参数缺失测试")
                        .datasources(List.of(src))
                        .datasets(List.of(prod))
                        .relationships(List.of())
                        .build();

        // 参数定义（无默认值）
        Parameter requiredParam =
                Parameter.builder()
                        .name("requiredParam")
                        .alias("必填参数")
                        .dataType(DataType.STRING)
                        .source(new ParamSource.External(true, null))
                        .build();

        // 单元格绑定：直接引用未传入的参数
        CellBinding paramCell =
                CellBinding.builder()
                        .cell(new CellRef("sheet1", 0, 0))
                        .value(new Value.ParamValue("requiredParam"))
                        .build();

        Report report =
                Report.builder()
                        .id("r_param_missing")
                        .name("参数缺失测试")
                        .dataModelId(dm.getId())
                        .templateId("tpl_param_missing")
                        .parameters(List.of(requiredParam))
                        .cellBindings(List.of(paramCell))
                        .loopBlocks(List.of())
                        .summaries(List.of())
                        .build();

        // 传入空参数 → 渲染不报错
        Sheet sheet = run(dm, report, Map.of(), "param-missing");

        // 参数未传入：单元格存在但值为 null（当前行为基线）
        Cell cell = findCell(sheet, 0, 0);
        if (cell != null) {
            assertTrue(cell.getValue() == null || cell.getValue().isNull(), "未传入的参数应产生空值单元格");
        }
    }

    // ============================================================
    // ---- 公共构造 / 运行 / 断言辅助 ----
    // ============================================================

    private static DataSource csv(String id, String path) {
        return DataSource.builder()
                .id(id)
                .name(id)
                .type(DataSourceType.CSV)
                .config(Map.of("path", path))
                .build();
    }

    private static CellBinding label(int row, int col, String text) {
        return CellBinding.builder()
                .cell(new CellRef("sheet1", row, col))
                .value(Templates.parse(text))
                .build();
    }

    /** 渲染 → 导出到 target/reports/{name}.xlsx → 回读 → 返回第一个 sheet */
    private Sheet run(DataModel dm, Report report, Map<String, Object> params, String name)
            throws Exception {
        Workbook workbook = renderer.render(dm, report, new ParamContext(params));

        Path dir = Path.of("target", "reports");
        Files.createDirectories(dir);
        Path file = dir.resolve(name + ".xlsx");
        Files.write(file, new ExcelExporter().export(workbook));
        System.out.println("[report] 导出: " + file.toAbsolutePath());

        Workbook back = new ExcelImporter().importFrom(Files.readAllBytes(file));
        return back.getSheets().get(0);
    }

    private static Cell findCell(Sheet sheet, int row, int col) {
        if (sheet.getCells() == null) return null;
        return sheet.getCells().stream()
                .filter(c -> c.getRow() == row && c.getCol() == col)
                .findFirst()
                .orElse(null);
    }

    private static String text(Sheet sheet, int row, int col) {
        Cell c = findCell(sheet, row, col);
        assertNotNull(c, "缺少单元格 (" + row + "," + col + ")");
        return c.getValue().asText();
    }
}
