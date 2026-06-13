package com.example.report.engine;

import com.codingapi.report.excel.ExcelExporter;
import com.codingapi.report.excel.ExcelImporter;
import com.codingapi.report.excel.pojo.Cell;
import com.codingapi.report.excel.pojo.Sheet;
import com.codingapi.report.excel.pojo.Workbook;
import com.example.report.model.DataModel;
import com.example.report.model.Report;
import com.example.report.model.grid.Aggregation;
import com.example.report.model.grid.CellBinding;
import com.example.report.model.grid.CellRef;
import com.example.report.model.grid.CompareOperator;
import com.example.report.model.grid.Condition;
import com.example.report.model.grid.ExpandMode;
import com.example.report.model.grid.Expansion;
import com.example.report.model.grid.FieldCell;
import com.example.report.model.grid.LoopBlock;
import com.example.report.model.grid.TextCell;
import com.example.report.model.param.ParamSource;
import com.example.report.model.param.Parameter;
import com.example.report.model.param.ValueRef;
import com.example.report.model.source.DataSource;
import com.example.report.model.source.DataSourceType;
import com.example.report.model.source.DataType;
import com.example.report.model.source.Dataset;
import com.example.report.model.source.Field;
import com.example.report.model.source.FieldRef;
import com.example.report.model.source.JoinType;
import com.example.report.model.source.Query;
import com.example.report.model.source.RelationOrigin;
import com.example.report.model.source.Relationship;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 四类报表结构的全链路验证：配置 → 计算 → <b>导出到本地 xlsx 文件</b> → 回读断言。
 * 每张报表都带标题与表头（静态文本格 TextCell）。
 *
 * <ol>
 *   <li>{@link #simpleList()} 简单列表</li>
 *   <li>{@link #mergedList()} 带合并的列表</li>
 *   <li>{@link #statistics()} 统计列表（单位/部门/人数）</li>
 *   <li>{@link #payslipLoop()} 循环块（横向薪资条）</li>
 * </ol>
 * 文件输出到 {@code target/reports/*.xlsx}。
 */
class ReportScenarioTest {

    private final ReportRenderer renderer = new ReportRenderer(List.of(new CsvDataExtractor()));

    // ============================================================
    // 1. 简单列表：标题 + 表头 + 商品名/价格
    // ============================================================

    @Test
    @DisplayName("简单列表：标题/表头 + 商品名/价格逐行 → xlsx")
    void simpleList() throws Exception {
        DataSource src = csv("ds_prod", "/data/products.csv");
        Dataset prod = Dataset.builder().id("d_prod").datasourceId("ds_prod").sourceTable("products.csv")
                .fields(List.of(
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("price").dataType(DataType.NUMBER).build()))
                .build();
        DataModel dm = DataModel.builder().id("dm_prod").name("商品模型")
                .datasources(List.of(src)).datasets(List.of(prod)).relationships(List.of()).build();

        TextCell title = label(0, 0, "商品清单");
        TextCell h1 = label(1, 0, "商品名");
        TextCell h2 = label(1, 1, "价格");
        FieldCell nameCol = listCol(new CellRef("sheet1", 2, 0), new FieldRef("d_prod", "name"));
        FieldCell priceCol = listCol(new CellRef("sheet1", 2, 1), new FieldRef("d_prod", "price"));
        Report report = report("r_prod", dm, List.of(title, h1, h2, nameCol, priceCol), List.of(), List.of());

        Sheet sheet = run(dm, report, Map.of(), "simple-list");

        assertEquals("商品清单", text(sheet, 0, 0));
        assertEquals("商品名", text(sheet, 1, 0));
        assertEquals("价格", text(sheet, 1, 1));
        assertEquals("苹果", text(sheet, 2, 0));
        assertEquals(5.0, number(sheet, 2, 1), 0.0001);
        assertEquals("香蕉", text(sheet, 3, 0));
        assertEquals("橙子", text(sheet, 4, 0));
        assertNull(findCell(sheet, 5, 0), "只有 3 行数据");
    }

    // ============================================================
    // 2. 带合并的列表：标题 + 表头 + 分类合并 + 商品/数量
    // ============================================================

    @Test
    @DisplayName("带合并列表：标题/表头 + 分类列 GROUP 合并 → xlsx")
    void mergedList() throws Exception {
        DataSource src = csv("ds_sales", "/data/sales.csv");
        Dataset sales = Dataset.builder().id("d_sales").datasourceId("ds_sales").sourceTable("sales.csv")
                .fields(List.of(
                        Field.builder().name("category").dataType(DataType.STRING).build(),
                        Field.builder().name("product").dataType(DataType.STRING).build(),
                        Field.builder().name("qty").dataType(DataType.NUMBER).build()))
                .build();
        DataModel dm = DataModel.builder().id("dm_sales").name("销售模型")
                .datasources(List.of(src)).datasets(List.of(sales)).relationships(List.of()).build();

        TextCell title = label(0, 0, "销售明细");
        TextCell h1 = label(1, 0, "分类");
        TextCell h2 = label(1, 1, "商品");
        TextCell h3 = label(1, 2, "数量");
        FieldCell catCol = FieldCell.builder()
                .cell(new CellRef("sheet1", 2, 0)).field(new FieldRef("d_sales", "category"))
                .expansion(Expansion.VERTICAL).expandMode(ExpandMode.GROUP).mergeRepeated(true).build();
        FieldCell prodCol = listCol(new CellRef("sheet1", 2, 1), new FieldRef("d_sales", "product"));
        FieldCell qtyCol = listCol(new CellRef("sheet1", 2, 2), new FieldRef("d_sales", "qty"));
        Report report = report("r_sales", dm,
                List.of(title, h1, h2, h3, catCol, prodCol, qtyCol), List.of(), List.of());

        Sheet sheet = run(dm, report, Map.of(), "merged-list");

        assertEquals("销售明细", text(sheet, 0, 0));
        assertEquals("分类", text(sheet, 1, 0));
        // 水果(苹果,香蕉) 在前，蔬菜(白菜) 在后；数据从第 2 行起
        assertEquals("水果", text(sheet, 2, 0));           // 合并区顶格
        assertEquals("苹果", text(sheet, 2, 1));
        assertEquals(10.0, number(sheet, 2, 2), 0.0001);
        assertEquals("香蕉", text(sheet, 3, 1));
        assertEquals("蔬菜", text(sheet, 4, 0));
        assertTrue(hasMerge(sheet, 2, 0, 2), "水果应跨 2 行合并");
    }

    // ============================================================
    // 3. 统计列表：标题 + 表头 + 单位 → 部门 → 人数(COUNT)
    // ============================================================

    @Test
    @DisplayName("统计列表：标题/表头 + 单位/部门分组统计人数，单位合并 → xlsx")
    void statistics() throws Exception {
        DataSource src = csv("ds_staff", "/data/staff.csv");
        Dataset staff = Dataset.builder().id("d_staff").datasourceId("ds_staff").sourceTable("staff.csv")
                .fields(List.of(
                        Field.builder().name("unit").dataType(DataType.STRING).build(),
                        Field.builder().name("dept").dataType(DataType.STRING).build(),
                        Field.builder().name("name").dataType(DataType.STRING).build()))
                .build();
        DataModel dm = DataModel.builder().id("dm_staff").name("人员模型")
                .datasources(List.of(src)).datasets(List.of(staff)).relationships(List.of()).build();

        TextCell title = label(0, 0, "人员统计");
        TextCell h1 = label(1, 0, "单位");
        TextCell h2 = label(1, 1, "部门");
        TextCell h3 = label(1, 2, "人数");
        CellRef unitRef = new CellRef("sheet1", 2, 0);
        CellRef deptRef = new CellRef("sheet1", 2, 1);
        FieldCell unitCol = FieldCell.builder().cell(unitRef).field(new FieldRef("d_staff", "unit"))
                .expansion(Expansion.VERTICAL).expandMode(ExpandMode.GROUP).mergeRepeated(true).build();
        FieldCell deptCol = FieldCell.builder().cell(deptRef).field(new FieldRef("d_staff", "dept"))
                .expansion(Expansion.VERTICAL).expandMode(ExpandMode.GROUP).mergeRepeated(true)
                .parentCell(unitRef).build();
        FieldCell countCol = FieldCell.builder().cell(new CellRef("sheet1", 2, 2))
                .field(new FieldRef("d_staff", "name"))
                .expansion(Expansion.VERTICAL).aggregation(Aggregation.COUNT).parentCell(deptRef).build();
        Report report = report("r_staff", dm,
                List.of(title, h1, h2, h3, unitCol, deptCol, countCol), List.of(), List.of());

        Sheet sheet = run(dm, report, Map.of(), "statistics");

        assertEquals("人员统计", text(sheet, 0, 0));
        assertEquals("人数", text(sheet, 1, 2));
        // 数据从第 2 行起；排序：市场中心 < 研发中心；研发中心内 前端组 < 后端组
        assertEquals("市场中心", text(sheet, 2, 0));
        assertEquals("销售组", text(sheet, 2, 1));
        assertEquals(1.0, number(sheet, 2, 2), 0.0001);
        assertEquals("研发中心", text(sheet, 3, 0));        // 合并区顶格
        assertEquals("前端组", text(sheet, 3, 1));
        assertEquals(1.0, number(sheet, 3, 2), 0.0001);
        assertEquals("后端组", text(sheet, 4, 1));
        assertEquals(2.0, number(sheet, 4, 2), 0.0001);    // 后端组 2 人
        assertTrue(hasMerge(sheet, 3, 0, 2), "研发中心应跨 2 行合并");
    }

    // ============================================================
    // 4. 循环块：横向薪资条（每人一块：标题行 + 表头行 + 数据行 + 空行）
    // ============================================================

    @Test
    @DisplayName("循环块：横向薪资条，每人重复 标题/表头/数据 三行（薪资跨源按 LoopField 查）→ xlsx")
    void payslipLoop() throws Exception {
        DataSource empSrc = csv("ds_emp", "/data/employees.csv");
        DataSource salSrc = csv("ds_sal", "/data/salaries.csv");
        Dataset emp = Dataset.builder().id("d_emp").datasourceId("ds_emp").sourceTable("employees.csv")
                .fields(List.of(
                        Field.builder().name("id").dataType(DataType.NUMBER).primaryKey(true).build(),
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("status").dataType(DataType.STRING).build()))
                .build();
        Dataset sal = Dataset.builder().id("d_sal").datasourceId("ds_sal").sourceTable("salaries.csv")
                .fields(List.of(
                        Field.builder().name("emp_id").dataType(DataType.NUMBER).build(),
                        Field.builder().name("base").dataType(DataType.NUMBER).build(),
                        Field.builder().name("bonus").dataType(DataType.NUMBER).build(),
                        Field.builder().name("total").dataType(DataType.NUMBER).build()))
                .build();
        Relationship rel = Relationship.builder().id("rel")
                .left(new FieldRef("d_sal", "emp_id")).right(new FieldRef("d_emp", "id"))
                .joinType(JoinType.INNER).origin(RelationOrigin.MANUAL).build();
        DataModel dm = DataModel.builder().id("dm_pay").name("薪资模型")
                .datasources(List.of(empSrc, salSrc)).datasets(List.of(emp, sal))
                .relationships(List.of(rel)).build();

        // 循环块：行 0..3（标题/表头/数据/空行），按在职员工驱动，高度 4
        LoopBlock loop = LoopBlock.builder().id("loop_emp").label("按员工")
                .start(new CellRef("sheet1", 0, 0)).end(new CellRef("sheet1", 3, 2))
                .source(Query.builder().datasetId("d_emp")
                        .filters(List.of(cond(new FieldRef("d_emp", "status"), CompareOperator.EQ,
                                new ValueRef.Literal("在职"))))
                        .groupBy(List.of()).build())
                .build();

        // 块内行0：动态标题 "${name}的薪资"（name 取循环当前员工）
        TextCell title = TextCell.builder().cell(new CellRef("sheet1", 0, 0))
                .template("${name}的薪资").build();
        // 块内行1：横向表头
        TextCell ht = label(1, 0, "总薪资");
        TextCell hb = label(1, 1, "岗位薪资");
        TextCell hp = label(1, 2, "绩效工资");
        // 块内行2：横向数据（跨源，按 LoopField 查）
        FieldCell total = salaryCell(2, 0, "total");
        FieldCell base = salaryCell(2, 1, "base");
        FieldCell bonus = salaryCell(2, 2, "bonus");

        Report report = report("r_pay", dm,
                List.of(title, ht, hb, hp, total, base, bonus), List.of(), List.of(loop));

        Sheet sheet = run(dm, report, Map.of(), "payslip");

        // 第 1 块 张三（行 0..3）
        assertEquals("张三的薪资", text(sheet, 0, 0));
        assertEquals("总薪资", text(sheet, 1, 0));
        assertEquals("岗位薪资", text(sheet, 1, 1));
        assertEquals("绩效工资", text(sheet, 1, 2));
        assertEquals(10000.0, number(sheet, 2, 0), 0.0001);
        assertEquals(8000.0, number(sheet, 2, 1), 0.0001);
        assertEquals(2000.0, number(sheet, 2, 2), 0.0001);

        // 第 2 块 李四（偏移 +4 → 行 4..7）
        assertEquals("李四的薪资", text(sheet, 4, 0));
        assertEquals("总薪资", text(sheet, 5, 0));
        assertEquals(10500.0, number(sheet, 6, 0), 0.0001);
        assertEquals(9000.0, number(sheet, 6, 1), 0.0001);
        assertEquals(1500.0, number(sheet, 6, 2), 0.0001);

        // 王五离职，被过滤，无第 3 块
        assertNull(findCell(sheet, 8, 0), "离职员工不出现");
    }

    // ============================================================
    // ---- 公共构造 / 运行 / 断言辅助 ----
    // ============================================================

    private static DataSource csv(String id, String path) {
        return DataSource.builder().id(id).name(id).type(DataSourceType.CSV)
                .config(Map.of("path", path)).build();
    }

    /** 静态文本格（标题/表头） */
    private static TextCell label(int row, int col, String text) {
        return TextCell.builder().cell(new CellRef("sheet1", row, col)).template(text).build();
    }

    private static FieldCell listCol(CellRef cell, FieldRef field) {
        return FieldCell.builder().cell(cell).field(field)
                .expansion(Expansion.VERTICAL).expandMode(ExpandMode.LIST).build();
    }

    /** 薪资条块内数据格：取 d_sal 的某字段，按 emp_id = 循环当前员工 id 过滤 */
    private static FieldCell salaryCell(int row, int col, String field) {
        return FieldCell.builder().cell(new CellRef("sheet1", row, col))
                .field(new FieldRef("d_sal", field)).expansion(Expansion.NONE)
                .conditions(List.of(cond(new FieldRef("d_sal", "emp_id"), CompareOperator.EQ,
                        new ValueRef.LoopField("loop_emp", "id")))).build();
    }

    private static Condition cond(FieldRef left, CompareOperator op, ValueRef value) {
        return Condition.builder().left(left).operator(op).value(value).build();
    }

    private static Report report(String id, DataModel dm, List<CellBinding> cells,
                                 List<Parameter> params, List<LoopBlock> loops) {
        return Report.builder().id(id).name(id).dataModelId(dm.getId()).templateId("tpl_" + id)
                .parameters(params).cellBindings(cells).loopBlocks(loops).build();
    }

    /** 渲染 → 导出到 target/reports/{name}.xlsx → 从本地文件回读 → 返回第一个 sheet */
    private Sheet run(DataModel dm, Report report, Map<String, Object> params, String name) throws Exception {
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
        if (sheet.getCells() == null) {
            return null;
        }
        return sheet.getCells().stream()
                .filter(c -> c.getRow() == row && c.getCol() == col).findFirst().orElse(null);
    }

    private static String text(Sheet sheet, int row, int col) {
        Cell c = findCell(sheet, row, col);
        assertNotNull(c, "缺少单元格 (" + row + "," + col + ")");
        return c.getValue().asText();
    }

    private static Double number(Sheet sheet, int row, int col) {
        Cell c = findCell(sheet, row, col);
        assertNotNull(c, "缺少单元格 (" + row + "," + col + ")");
        return c.getValue().asDouble();
    }

    private static boolean hasMerge(Sheet sheet, int startRow, int startCol, int rowSpan) {
        if (sheet.getMerges() == null) {
            return false;
        }
        return sheet.getMerges().stream().anyMatch(m ->
                m.getStartRow() == startRow && m.getStartCol() == startCol && m.getRowSpan() == rowSpan);
    }
}
