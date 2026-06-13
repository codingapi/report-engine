package com.codingapi.report.engine;

import com.codingapi.report.excel.ExcelExporter;
import com.codingapi.report.excel.ExcelImporter;
import com.codingapi.report.excel.pojo.Cell;
import com.codingapi.report.excel.pojo.Sheet;
import com.codingapi.report.excel.pojo.Workbook;
import com.codingapi.report.model.DataModel;
import com.codingapi.report.model.Report;
import com.codingapi.report.model.grid.Aggregation;
import com.codingapi.report.model.grid.CellBinding;
import com.codingapi.report.model.grid.CellRef;
import com.codingapi.report.model.grid.CompareOperator;
import com.codingapi.report.model.grid.Condition;
import com.codingapi.report.model.grid.ExpandMode;
import com.codingapi.report.model.grid.Expansion;
import com.codingapi.report.model.grid.FieldCell;
import com.codingapi.report.model.grid.LoopBlock;
import com.codingapi.report.model.grid.SummaryCell;
import com.codingapi.report.model.grid.SummaryRow;
import com.codingapi.report.model.grid.TextCell;
import com.codingapi.report.model.param.ParamSource;
import com.codingapi.report.model.param.Parameter;
import com.codingapi.report.model.param.ValueRef;
import com.codingapi.report.model.source.DataSource;
import com.codingapi.report.model.source.DataSourceType;
import com.codingapi.report.model.source.DataType;
import com.codingapi.report.model.source.Dataset;
import com.codingapi.report.model.source.Field;
import com.codingapi.report.model.source.FieldRef;
import com.codingapi.report.model.source.JoinType;
import com.codingapi.report.model.source.Query;
import com.codingapi.report.model.source.RelationOrigin;
import com.codingapi.report.model.source.Relationship;
import com.codingapi.report.model.source.UnionMember;
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
        // 总计行：合计 + 总价（groupBy=null）
        SummaryRow total = SummaryRow.builder().groupBy(null).cells(List.of(
                SummaryCell.label(0, "合计"),
                SummaryCell.agg(1, new FieldRef("d_prod", "price"), Aggregation.SUM))).build();
        Report report = report("r_prod", dm, List.of(title, h1, h2, nameCol, priceCol),
                List.of(), List.of(), List.of(total));

        Sheet sheet = run(dm, report, Map.of(), "simple-list");

        assertEquals("商品清单", text(sheet, 0, 0));
        assertEquals("商品名", text(sheet, 1, 0));
        assertEquals("价格", text(sheet, 1, 1));
        assertEquals("苹果", text(sheet, 2, 0));
        assertEquals(5.0, number(sheet, 2, 1), 0.0001);
        assertEquals("香蕉", text(sheet, 3, 0));
        assertEquals("橙子", text(sheet, 4, 0));
        // 列表后追加合计行：苹果5+香蕉3+橙子4=12
        assertEquals("合计", text(sheet, 5, 0));
        assertEquals(12.0, number(sheet, 5, 1), 0.0001);
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
        TextCell h4 = label(1, 3, "总人数");
        CellRef unitRef = new CellRef("sheet1", 2, 0);
        CellRef deptRef = new CellRef("sheet1", 2, 1);
        FieldCell unitCol = FieldCell.builder().cell(unitRef).field(new FieldRef("d_staff", "unit"))
                .expansion(Expansion.VERTICAL).expandMode(ExpandMode.GROUP).mergeRepeated(true).build();
        FieldCell deptCol = FieldCell.builder().cell(deptRef).field(new FieldRef("d_staff", "dept"))
                .expansion(Expansion.VERTICAL).expandMode(ExpandMode.GROUP).mergeRepeated(true)
                .parentCell(unitRef).build();
        // 人数：按 部门 粒度 COUNT（parent=部门）
        FieldCell countCol = FieldCell.builder().cell(new CellRef("sheet1", 2, 2))
                .field(new FieldRef("d_staff", "name"))
                .expansion(Expansion.VERTICAL).aggregation(Aggregation.COUNT).parentCell(deptRef).build();
        // 总人数：按 单位 粒度 COUNT（parent=单位），跨该单位的部门行合并
        FieldCell unitTotalCol = FieldCell.builder().cell(new CellRef("sheet1", 2, 3))
                .field(new FieldRef("d_staff", "name"))
                .expansion(Expansion.VERTICAL).aggregation(Aggregation.COUNT).parentCell(unitRef)
                .mergeRepeated(true).build();
        Report report = report("r_staff", dm,
                List.of(title, h1, h2, h3, h4, unitCol, deptCol, countCol, unitTotalCol), List.of(), List.of());

        Sheet sheet = run(dm, report, Map.of(), "statistics");

        assertEquals("人员统计", text(sheet, 0, 0));
        assertEquals("人数", text(sheet, 1, 2));
        assertEquals("总人数", text(sheet, 1, 3));
        // 数据从第 2 行起；排序：市场中心 < 研发中心；研发中心内 前端组 < 后端组
        assertEquals("市场中心", text(sheet, 2, 0));
        assertEquals("销售组", text(sheet, 2, 1));
        assertEquals(1.0, number(sheet, 2, 2), 0.0001);
        assertEquals(1.0, number(sheet, 2, 3), 0.0001);    // 市场中心总人数 1
        assertEquals("研发中心", text(sheet, 3, 0));        // 合并区顶格
        assertEquals("前端组", text(sheet, 3, 1));
        assertEquals(1.0, number(sheet, 3, 2), 0.0001);
        assertEquals(3.0, number(sheet, 3, 3), 0.0001);    // 研发中心总人数 3（顶格）
        assertEquals("后端组", text(sheet, 4, 1));
        assertEquals(2.0, number(sheet, 4, 2), 0.0001);    // 后端组 2 人
        assertTrue(hasMerge(sheet, 3, 0, 2), "研发中心(单位)应跨 2 行合并");
        assertTrue(hasMerge(sheet, 3, 3, 2), "研发中心总人数列应跨 2 行合并");
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
    // 5. 主从关联 + 合并：员工(1) ↔ 学历(N)，主表列跨多条学历合并
    // ============================================================

    @Test
    @DisplayName("主从关联+合并：员工 join 学历(1:N)，姓名/性别/年龄跨该员工的多条学历合并 → xlsx")
    void masterDetailMergedList() throws Exception {
        DataSource empSrc = csv("ds_emp2", "/data/emp_basic.csv");
        DataSource eduSrc = csv("ds_edu", "/data/emp_education.csv");
        Dataset emp = Dataset.builder().id("d_emp2").datasourceId("ds_emp2").sourceTable("emp_basic.csv")
                .fields(List.of(
                        Field.builder().name("id").dataType(DataType.NUMBER).primaryKey(true).build(),
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("gender").dataType(DataType.STRING).build(),
                        Field.builder().name("age").dataType(DataType.NUMBER).build()))
                .build();
        Dataset edu = Dataset.builder().id("d_edu").datasourceId("ds_edu").sourceTable("emp_education.csv")
                .fields(List.of(
                        Field.builder().name("emp_id").dataType(DataType.NUMBER).build(),
                        Field.builder().name("school").dataType(DataType.STRING).build(),
                        Field.builder().name("major").dataType(DataType.STRING).build(),
                        Field.builder().name("graduate_time").dataType(DataType.NUMBER).build()))
                .build();
        // 关系：学历.emp_id = 员工.id（1 个员工 → 多条学历）
        Relationship rel = Relationship.builder().id("rel_emp_edu")
                .left(new FieldRef("d_edu", "emp_id")).right(new FieldRef("d_emp2", "id"))
                .joinType(JoinType.INNER).origin(RelationOrigin.MANUAL).build();
        DataModel dm = DataModel.builder().id("dm_edu").name("员工学历模型")
                .datasources(List.of(empSrc, eduSrc)).datasets(List.of(emp, edu))
                .relationships(List.of(rel)).build();

        // 标题 + 表头
        TextCell title = label(0, 0, "员工学历信息表");
        TextCell h0 = label(1, 0, "姓名");
        TextCell h1 = label(1, 1, "性别");
        TextCell h2 = label(1, 2, "年龄");
        TextCell h3 = label(1, 3, "学校名称");
        TextCell h4 = label(1, 4, "专业名称");
        TextCell h5 = label(1, 5, "毕业时间");

        // 主表列：姓名/性别/年龄 = GROUP + 合并，父格链 姓名→性别→年龄（同一员工共变）
        CellRef nameRef = new CellRef("sheet1", 2, 0);
        CellRef genderRef = new CellRef("sheet1", 2, 1);
        FieldCell nameCol = groupMerge(nameRef, new FieldRef("d_emp2", "name"), null);
        FieldCell genderCol = groupMerge(genderRef, new FieldRef("d_emp2", "gender"), nameRef);
        FieldCell ageCol = groupMerge(new CellRef("sheet1", 2, 2), new FieldRef("d_emp2", "age"), genderRef);
        // 从表列：学校/专业/毕业 = LIST 明细
        FieldCell schoolCol = listCol(new CellRef("sheet1", 2, 3), new FieldRef("d_edu", "school"));
        FieldCell majorCol = listCol(new CellRef("sheet1", 2, 4), new FieldRef("d_edu", "major"));
        FieldCell gradCol = listCol(new CellRef("sheet1", 2, 5), new FieldRef("d_edu", "graduate_time"));

        Report report = report("r_edu", dm,
                List.of(title, h0, h1, h2, h3, h4, h5, nameCol, genderCol, ageCol, schoolCol, majorCol, gradCol),
                List.of(), List.of());

        Sheet sheet = run(dm, report, Map.of(), "master-detail-merge");

        assertEquals("员工学历信息表", text(sheet, 0, 0));
        assertEquals("毕业时间", text(sheet, 1, 5));

        // 排序：张三(5F20) < 李四(674E)。张三 2 条学历(行2-3)，李四 1 条(行4)
        assertEquals("张三", text(sheet, 2, 0));           // 合并区顶格
        assertEquals("男", text(sheet, 2, 1));
        assertEquals(32.0, number(sheet, 2, 2), 0.0001);
        assertEquals("北京大学", text(sheet, 2, 3));
        assertEquals("北京大学研究生院", text(sheet, 3, 3));
        assertEquals(2013.0, number(sheet, 3, 5), 0.0001);
        assertEquals("李四", text(sheet, 4, 0));
        assertEquals("清华大学", text(sheet, 4, 3));

        // 张三的姓名/性别/年龄三列都跨 行2-3 合并；李四单行不合并
        assertTrue(hasMerge(sheet, 2, 0, 2), "姓名应跨张三的 2 条学历合并");
        assertTrue(hasMerge(sheet, 2, 1, 2), "性别应跨张三的 2 条学历合并");
        assertTrue(hasMerge(sheet, 2, 2, 2), "年龄应跨张三的 2 条学历合并");
    }

    // ============================================================
    // 6. 分组小计 + 总计：单位部门薪资统计表（明细 → 单位小计 → 总计）
    // ============================================================

    @Test
    @DisplayName("分组小计+总计：每个单位的部门明细后插单位小计，全表末尾插总计 → xlsx")
    void salarySubtotalAndGrandTotal() throws Exception {
        DataSource src = csv("ds_sal_detail", "/data/salary_detail.csv");
        Dataset sal = Dataset.builder().id("d_sd").datasourceId("ds_sal_detail").sourceTable("salary_detail.csv")
                .fields(List.of(
                        Field.builder().name("unit").dataType(DataType.STRING).build(),
                        Field.builder().name("dept").dataType(DataType.STRING).build(),
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("salary").dataType(DataType.NUMBER).build()))
                .build();
        DataModel dm = DataModel.builder().id("dm_sd").name("薪资明细模型")
                .datasources(List.of(src)).datasets(List.of(sal)).relationships(List.of()).build();

        TextCell title = label(0, 0, "单位部门薪资统计表");
        TextCell h0 = label(1, 0, "单位");
        TextCell h1 = label(1, 1, "部门");
        TextCell h2 = label(1, 2, "姓名");
        TextCell h3 = label(1, 3, "薪资");

        CellRef unitRef = new CellRef("sheet1", 2, 0);
        FieldCell unitCol = groupMerge(unitRef, new FieldRef("d_sd", "unit"), null);
        FieldCell deptCol = groupMerge(new CellRef("sheet1", 2, 1), new FieldRef("d_sd", "dept"), unitRef);
        FieldCell nameCol = listCol(new CellRef("sheet1", 2, 2), new FieldRef("d_sd", "name"));
        FieldCell salaryCol = listCol(new CellRef("sheet1", 2, 3), new FieldRef("d_sd", "salary"));

        // 单位小计：每个单位结束后，部门列放"${单位}小计"标签，薪资列放 SUM
        SummaryRow unitSubtotal = SummaryRow.builder().groupBy(new FieldRef("d_sd", "unit")).cells(List.of(
                SummaryCell.label(1, "${group}小计"),
                SummaryCell.agg(3, new FieldRef("d_sd", "salary"), Aggregation.SUM))).build();
        // 总计：全表末尾
        SummaryRow grandTotal = SummaryRow.builder().groupBy(null).cells(List.of(
                SummaryCell.label(0, "总计"),
                SummaryCell.agg(3, new FieldRef("d_sd", "salary"), Aggregation.SUM))).build();

        Report report = report("r_sd", dm,
                List.of(title, h0, h1, h2, h3, unitCol, deptCol, nameCol, salaryCol),
                List.of(), List.of(), List.of(unitSubtotal, grandTotal));

        Sheet sheet = run(dm, report, Map.of(), "salary-subtotal");

        assertEquals("单位部门薪资统计表", text(sheet, 0, 0));
        // 排序：市场中心 < 研发中心；研发中心内 前端组 < 后端组
        // row2 市场中心/销售组/赵六/7000
        assertEquals("市场中心", text(sheet, 2, 0));
        assertEquals("赵六", text(sheet, 2, 2));
        assertEquals(7000.0, number(sheet, 2, 3), 0.0001);
        // row3 市场中心小计 7000
        assertEquals("市场中心小计", text(sheet, 3, 1));
        assertEquals(7000.0, number(sheet, 3, 3), 0.0001);
        // row4-6 研发中心：前端王五8000 / 后端张三10000 / 后端李四9000
        assertEquals("研发中心", text(sheet, 4, 0));        // 单位合并顶格
        assertEquals("前端组", text(sheet, 4, 1));
        assertEquals("王五", text(sheet, 4, 2));
        assertEquals("后端组", text(sheet, 5, 1));          // 部门合并顶格
        assertEquals("张三", text(sheet, 5, 2));
        assertEquals("李四", text(sheet, 6, 2));
        // row7 研发中心小计 27000
        assertEquals("研发中心小计", text(sheet, 7, 1));
        assertEquals(27000.0, number(sheet, 7, 3), 0.0001);
        // row8 总计 34000
        assertEquals("总计", text(sheet, 8, 0));
        assertEquals(34000.0, number(sheet, 8, 3), 0.0001);

        // 单位列：研发中心跨明细行 4-6 合并（不含小计行）
        assertTrue(hasMerge(sheet, 4, 0, 3), "研发中心应跨其 3 条明细合并");
        // 部门列：后端组跨 5-6 合并
        assertTrue(hasMerge(sheet, 5, 1, 2), "后端组应跨 2 条明细合并");
    }

    // ============================================================
    // 7. 多数据集 UNION：两个部门人员（不同源/不同列名）拼成一张列表
    // ============================================================

    @Test
    @DisplayName("UNION：A、B 两部门人员（不同源、列名不同）按映射对齐后纵向拼成一张列表 → xlsx")
    void unionTwoDepartments() throws Exception {
        DataSource aSrc = csv("ds_a", "/data/dept_a.csv");
        DataSource bSrc = csv("ds_b", "/data/dept_b.csv");
        // A 列名 name/gender/age
        Dataset a = Dataset.builder().id("d_a").datasourceId("ds_a").sourceTable("dept_a.csv")
                .fields(List.of(
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("gender").dataType(DataType.STRING).build(),
                        Field.builder().name("age").dataType(DataType.NUMBER).build()))
                .build();
        // B 列名 xm/xb/nl（与 A 不同）
        Dataset b = Dataset.builder().id("d_b").datasourceId("ds_b").sourceTable("dept_b.csv")
                .fields(List.of(
                        Field.builder().name("xm").dataType(DataType.STRING).build(),
                        Field.builder().name("xb").dataType(DataType.STRING).build(),
                        Field.builder().name("nl").dataType(DataType.NUMBER).build()))
                .build();
        // UNION 派生数据集：统一列 姓名/性别/年龄，各成员按映射对齐
        Dataset people = Dataset.builder().id("d_people").alias("全部人员")
                .fields(List.of(
                        Field.builder().name("姓名").dataType(DataType.STRING).build(),
                        Field.builder().name("性别").dataType(DataType.STRING).build(),
                        Field.builder().name("年龄").dataType(DataType.NUMBER).build()))
                .union(List.of(
                        new UnionMember("d_a", Map.of("姓名", "name", "性别", "gender", "年龄", "age")),
                        new UnionMember("d_b", Map.of("姓名", "xm", "性别", "xb", "年龄", "nl"))))
                .build();
        DataModel dm = DataModel.builder().id("dm_people").name("人员合集模型")
                .datasources(List.of(aSrc, bSrc)).datasets(List.of(a, b, people))
                .relationships(List.of()).build();

        TextCell title = label(0, 0, "全部人员名单");
        TextCell h0 = label(1, 0, "姓名");
        TextCell h1 = label(1, 1, "性别");
        TextCell h2 = label(1, 2, "年龄");
        FieldCell nameCol = listCol(new CellRef("sheet1", 2, 0), new FieldRef("d_people", "姓名"));
        FieldCell genderCol = listCol(new CellRef("sheet1", 2, 1), new FieldRef("d_people", "性别"));
        FieldCell ageCol = listCol(new CellRef("sheet1", 2, 2), new FieldRef("d_people", "年龄"));
        Report report = report("r_people", dm,
                List.of(title, h0, h1, h2, nameCol, genderCol, ageCol), List.of(), List.of());

        Sheet sheet = run(dm, report, Map.of(), "union-people");

        assertEquals("全部人员名单", text(sheet, 0, 0));
        // A 的两人在前，B 的两人在后（保持成员顺序）
        assertEquals("A张三", text(sheet, 2, 0));
        assertEquals("男", text(sheet, 2, 1));
        assertEquals(32.0, number(sheet, 2, 2), 0.0001);
        assertEquals("A李四", text(sheet, 3, 0));
        assertEquals("B王五", text(sheet, 4, 0));
        assertEquals(34.0, number(sheet, 4, 2), 0.0001);
        assertEquals("B赵六", text(sheet, 5, 0));
        assertEquals(40.0, number(sheet, 5, 2), 0.0001);
        assertNull(findCell(sheet, 6, 0), "共 4 人");
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

    /** 分组列（去重 + 合并），可指定父格构成层级 */
    private static FieldCell groupMerge(CellRef cell, FieldRef field, CellRef parent) {
        return FieldCell.builder().cell(cell).field(field)
                .expansion(Expansion.VERTICAL).expandMode(ExpandMode.GROUP).mergeRepeated(true)
                .parentCell(parent).build();
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
        return report(id, dm, cells, params, loops, List.of());
    }

    private static Report report(String id, DataModel dm, List<CellBinding> cells,
                                 List<Parameter> params, List<LoopBlock> loops, List<SummaryRow> summaries) {
        return Report.builder().id(id).name(id).dataModelId(dm.getId()).templateId("tpl_" + id)
                .parameters(params).cellBindings(cells).loopBlocks(loops).summaries(summaries).build();
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
