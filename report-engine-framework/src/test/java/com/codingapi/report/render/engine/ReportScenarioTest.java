package com.codingapi.report.render.engine;

import com.codingapi.report.param.ParamContext;
import com.codingapi.report.data.datasource.csv.CsvDataExtractor;

import com.codingapi.report.excel.ExcelExporter;
import com.codingapi.report.excel.ExcelImporter;
import com.codingapi.report.excel.pojo.Cell;
import com.codingapi.report.excel.pojo.Merge;
import com.codingapi.report.excel.pojo.Sheet;
import com.codingapi.report.excel.pojo.Workbook;
import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.render.Report;

import com.codingapi.report.render.grid.CellBinding;
import com.codingapi.report.expression.Value;
import com.codingapi.report.expression.Templates;
import com.codingapi.report.render.grid.CellRef;
import com.codingapi.report.operator.condition.CompareOperator;
import com.codingapi.report.operator.condition.Condition;
import com.codingapi.report.render.grid.ExpandMode;
import com.codingapi.report.render.grid.Expansion;
import com.codingapi.report.render.grid.LoopBlock;
import com.codingapi.report.render.grid.SummaryCell;
import com.codingapi.report.render.grid.SummaryRow;
import com.codingapi.report.param.ParamSource;
import com.codingapi.report.param.Parameter;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.DataSourceType;
import com.codingapi.report.data.dataset.DataType;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.dataset.UnionDataset;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.data.relation.JoinType;
import com.codingapi.report.data.dataset.Query;
import com.codingapi.report.data.relation.RelationOrigin;
import com.codingapi.report.data.relation.Relationship;
import com.codingapi.report.data.dataset.UnionMember;
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
 * 每张报表都带标题与表头（静态文本格 CellBinding）。
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
        Dataset prod = TableDataset.builder().id("d_prod").datasourceId("ds_prod").sourceTable("products.csv")
                .fields(List.of(
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("price").dataType(DataType.NUMBER).build()))
                .build();
        DataModel dm = DataModel.builder().id("dm_prod").name("商品模型")
                .datasources(List.of(src)).datasets(List.of(prod)).relationships(List.of()).build();

        CellBinding title = label(0, 0, "商品清单");
        CellBinding h1 = label(1, 0, "商品名");
        CellBinding h2 = label(1, 1, "价格");
        CellBinding nameCol = listCol(new CellRef("sheet1", 2, 0), new FieldRef("d_prod", "name"));
        CellBinding priceCol = listCol(new CellRef("sheet1", 2, 1), new FieldRef("d_prod", "price"));
        // 总计行：合计 + 总价（groupBy=null）
        SummaryRow total = SummaryRow.builder().groupBy(null).fromColumn(0).toColumn(1).cells(List.of(
                SummaryCell.label(0, "合计"),
                SummaryCell.agg(1, new FieldRef("d_prod", "price"), "SUM"))).build();
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
        Dataset sales = TableDataset.builder().id("d_sales").datasourceId("ds_sales").sourceTable("sales.csv")
                .fields(List.of(
                        Field.builder().name("category").dataType(DataType.STRING).build(),
                        Field.builder().name("product").dataType(DataType.STRING).build(),
                        Field.builder().name("qty").dataType(DataType.NUMBER).build()))
                .build();
        DataModel dm = DataModel.builder().id("dm_sales").name("销售模型")
                .datasources(List.of(src)).datasets(List.of(sales)).relationships(List.of()).build();

        CellBinding title = label(0, 0, "销售明细");
        CellBinding h1 = label(1, 0, "分类");
        CellBinding h2 = label(1, 1, "商品");
        CellBinding h3 = label(1, 2, "数量");
        CellBinding catCol = CellBinding.builder()
                .cell(new CellRef("sheet1", 2, 0)).value(new Value.FieldValue(new FieldRef("d_sales", "category")))
                .expansion(Expansion.VERTICAL).expandMode(ExpandMode.GROUP).mergeRepeated(true).build();
        CellBinding prodCol = listCol(new CellRef("sheet1", 2, 1), new FieldRef("d_sales", "product"));
        CellBinding qtyCol = listCol(new CellRef("sheet1", 2, 2), new FieldRef("d_sales", "qty"));
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
        Dataset staff = TableDataset.builder().id("d_staff").datasourceId("ds_staff").sourceTable("staff.csv")
                .fields(List.of(
                        Field.builder().name("unit").dataType(DataType.STRING).build(),
                        Field.builder().name("dept").dataType(DataType.STRING).build(),
                        Field.builder().name("name").dataType(DataType.STRING).build()))
                .build();
        DataModel dm = DataModel.builder().id("dm_staff").name("人员模型")
                .datasources(List.of(src)).datasets(List.of(staff)).relationships(List.of()).build();

        CellBinding title = label(0, 0, "人员统计");
        CellBinding h1 = label(1, 0, "单位");
        CellBinding h2 = label(1, 1, "部门");
        CellBinding h3 = label(1, 2, "人数");
        CellBinding h4 = label(1, 3, "总人数");
        CellRef unitRef = new CellRef("sheet1", 2, 0);
        CellRef deptRef = new CellRef("sheet1", 2, 1);
        CellBinding unitCol = CellBinding.builder().cell(unitRef).value(new Value.FieldValue(new FieldRef("d_staff", "unit")))
                .expansion(Expansion.VERTICAL).expandMode(ExpandMode.GROUP).mergeRepeated(true).build();
        CellBinding deptCol = CellBinding.builder().cell(deptRef).value(new Value.FieldValue(new FieldRef("d_staff", "dept")))
                .expansion(Expansion.VERTICAL).expandMode(ExpandMode.GROUP).mergeRepeated(true)
                .parentCell(unitRef).build();
        // 人数：按 部门 粒度 COUNT（parent=部门）
        CellBinding countCol = CellBinding.builder().cell(new CellRef("sheet1", 2, 2))
                .value(new Value.Aggregate("COUNT", new Value.FieldValue(new FieldRef("d_staff", "name"))))
                .expansion(Expansion.VERTICAL).parentCell(deptRef).build();
        // 总人数：按 单位 粒度 COUNT（parent=单位），跨该单位的部门行合并
        CellBinding unitTotalCol = CellBinding.builder().cell(new CellRef("sheet1", 2, 3))
                .value(new Value.Aggregate("COUNT", new Value.FieldValue(new FieldRef("d_staff", "name"))))
                .expansion(Expansion.VERTICAL).parentCell(unitRef)
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
        Dataset emp = TableDataset.builder().id("d_emp").datasourceId("ds_emp").sourceTable("employees.csv")
                .fields(List.of(
                        Field.builder().name("id").dataType(DataType.NUMBER).primaryKey(true).build(),
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("status").dataType(DataType.STRING).build()))
                .build();
        Dataset sal = TableDataset.builder().id("d_sal").datasourceId("ds_sal").sourceTable("salaries.csv")
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
                        .filters(List.of(cond(new Value.FieldValue(new FieldRef("d_emp", "status")), CompareOperator.EQ,
                                new Value.Literal("在职"))))
                        .groupBy(List.of()).build())
                .build();

        // 块内行0：动态标题 "${name}的薪资"（name 取循环当前员工）
        CellBinding title = CellBinding.builder().cell(new CellRef("sheet1", 0, 0))
                .value(Templates.parse("${name}的薪资")).build();
        // 块内行1：横向表头
        CellBinding ht = label(1, 0, "总薪资");
        CellBinding hb = label(1, 1, "岗位薪资");
        CellBinding hp = label(1, 2, "绩效工资");
        // 块内行2：横向数据（跨源，按 LoopField 查）
        CellBinding total = salaryCell(2, 0, "total");
        CellBinding base = salaryCell(2, 1, "base");
        CellBinding bonus = salaryCell(2, 2, "bonus");

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
        Dataset emp = TableDataset.builder().id("d_emp2").datasourceId("ds_emp2").sourceTable("emp_basic.csv")
                .fields(List.of(
                        Field.builder().name("id").dataType(DataType.NUMBER).primaryKey(true).build(),
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("gender").dataType(DataType.STRING).build(),
                        Field.builder().name("age").dataType(DataType.NUMBER).build()))
                .build();
        Dataset edu = TableDataset.builder().id("d_edu").datasourceId("ds_edu").sourceTable("emp_education.csv")
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
        CellBinding title = label(0, 0, "员工学历信息表");
        CellBinding h0 = label(1, 0, "姓名");
        CellBinding h1 = label(1, 1, "性别");
        CellBinding h2 = label(1, 2, "年龄");
        CellBinding h3 = label(1, 3, "学校名称");
        CellBinding h4 = label(1, 4, "专业名称");
        CellBinding h5 = label(1, 5, "毕业时间");

        // 主表列：姓名/性别/年龄 = GROUP + 合并，父格链 姓名→性别→年龄（同一员工共变）
        CellRef nameRef = new CellRef("sheet1", 2, 0);
        CellRef genderRef = new CellRef("sheet1", 2, 1);
        CellBinding nameCol = groupMerge(nameRef, new FieldRef("d_emp2", "name"), null);
        CellBinding genderCol = groupMerge(genderRef, new FieldRef("d_emp2", "gender"), nameRef);
        CellBinding ageCol = groupMerge(new CellRef("sheet1", 2, 2), new FieldRef("d_emp2", "age"), genderRef);
        // 从表列：学校/专业/毕业 = LIST 明细
        CellBinding schoolCol = listCol(new CellRef("sheet1", 2, 3), new FieldRef("d_edu", "school"));
        CellBinding majorCol = listCol(new CellRef("sheet1", 2, 4), new FieldRef("d_edu", "major"));
        CellBinding gradCol = listCol(new CellRef("sheet1", 2, 5), new FieldRef("d_edu", "graduate_time"));

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
        Dataset sal = TableDataset.builder().id("d_sd").datasourceId("ds_sal_detail").sourceTable("salary_detail.csv")
                .fields(List.of(
                        Field.builder().name("unit").dataType(DataType.STRING).build(),
                        Field.builder().name("dept").dataType(DataType.STRING).build(),
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("salary").dataType(DataType.NUMBER).build()))
                .build();
        DataModel dm = DataModel.builder().id("dm_sd").name("薪资明细模型")
                .datasources(List.of(src)).datasets(List.of(sal)).relationships(List.of()).build();

        CellBinding title = label(0, 0, "单位部门薪资统计表");
        CellBinding h0 = label(1, 0, "单位");
        CellBinding h1 = label(1, 1, "部门");
        CellBinding h2 = label(1, 2, "姓名");
        CellBinding h3 = label(1, 3, "薪资");

        CellRef unitRef = new CellRef("sheet1", 2, 0);
        CellBinding unitCol = groupMerge(unitRef, new FieldRef("d_sd", "unit"), null);
        CellBinding deptCol = groupMerge(new CellRef("sheet1", 2, 1), new FieldRef("d_sd", "dept"), unitRef);
        CellBinding nameCol = listCol(new CellRef("sheet1", 2, 2), new FieldRef("d_sd", "name"));
        CellBinding salaryCol = listCol(new CellRef("sheet1", 2, 3), new FieldRef("d_sd", "salary"));

        // 单位小计：每个单位结束后，部门列放"${单位}小计"标签，薪资列放 SUM
        SummaryRow unitSubtotal = SummaryRow.builder().groupBy(new FieldRef("d_sd", "unit"))
                .fromColumn(0).toColumn(3).cells(List.of(
                SummaryCell.label(1, "${group}小计"),
                SummaryCell.agg(3, new FieldRef("d_sd", "salary"), "SUM"))).build();
        // 总计：全表末尾
        SummaryRow grandTotal = SummaryRow.builder().groupBy(null)
                .fromColumn(0).toColumn(3).cells(List.of(
                SummaryCell.label(0, "总计"),
                SummaryCell.agg(3, new FieldRef("d_sd", "salary"), "SUM"))).build();

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
        Dataset a = TableDataset.builder().id("d_a").datasourceId("ds_a").sourceTable("dept_a.csv")
                .fields(List.of(
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("gender").dataType(DataType.STRING).build(),
                        Field.builder().name("age").dataType(DataType.NUMBER).build()))
                .build();
        // B 列名 xm/xb/nl（与 A 不同）
        Dataset b = TableDataset.builder().id("d_b").datasourceId("ds_b").sourceTable("dept_b.csv")
                .fields(List.of(
                        Field.builder().name("xm").dataType(DataType.STRING).build(),
                        Field.builder().name("xb").dataType(DataType.STRING).build(),
                        Field.builder().name("nl").dataType(DataType.NUMBER).build()))
                .build();
        // UNION 派生数据集：统一列 姓名/性别/年龄，各成员按映射对齐
        Dataset people = UnionDataset.builder().id("d_people").alias("全部人员")
                .fields(List.of(
                        Field.builder().name("姓名").dataType(DataType.STRING).build(),
                        Field.builder().name("性别").dataType(DataType.STRING).build(),
                        Field.builder().name("年龄").dataType(DataType.NUMBER).build()))
                .members(List.of(
                        new UnionMember("d_a", Map.of("姓名", "name", "性别", "gender", "年龄", "age")),
                        new UnionMember("d_b", Map.of("姓名", "xm", "性别", "xb", "年龄", "nl"))))
                .build();
        DataModel dm = DataModel.builder().id("dm_people").name("人员合集模型")
                .datasources(List.of(aSrc, bSrc)).datasets(List.of(a, b, people))
                .relationships(List.of()).build();

        CellBinding title = label(0, 0, "全部人员名单");
        CellBinding h0 = label(1, 0, "姓名");
        CellBinding h1 = label(1, 1, "性别");
        CellBinding h2 = label(1, 2, "年龄");
        CellBinding nameCol = listCol(new CellRef("sheet1", 2, 0), new FieldRef("d_people", "姓名"));
        CellBinding genderCol = listCol(new CellRef("sheet1", 2, 1), new FieldRef("d_people", "性别"));
        CellBinding ageCol = listCol(new CellRef("sheet1", 2, 2), new FieldRef("d_people", "年龄"));
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
    // 7. 独立数据带：两个无关系数据集并列展开
    // ============================================================

    @Test
    @DisplayName("独立数据带：staff(4行) 和 products(3行) 无关系并列 → 各自独立展开")
    void independentBands() throws Exception {
        // 两个数据集，无 Relationship
        DataSource srcStaff = csv("ds_staff", "/data/staff.csv");
        Dataset staff = TableDataset.builder().id("d_staff").datasourceId("ds_staff").sourceTable("staff.csv")
                .fields(List.of(
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("unit").dataType(DataType.STRING).build()))
                .build();
        DataSource srcProd = csv("ds_prod", "/data/products.csv");
        Dataset prod = TableDataset.builder().id("d_prod").datasourceId("ds_prod").sourceTable("products.csv")
                .fields(List.of(
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("price").dataType(DataType.NUMBER).build()))
                .build();
        DataModel dm = DataModel.builder().id("dm_indep").name("独立模型")
                .datasources(List.of(srcStaff, srcProd))
                .datasets(List.of(staff, prod))
                .relationships(List.of())  // 无关系
                .build();

        // Row 0: 表头
        CellBinding h1 = label(0, 0, "姓名");
        CellBinding h2 = label(0, 1, "单位");
        CellBinding h3 = label(0, 2, "商品名");
        CellBinding h4 = label(0, 3, "价格");
        // Row 1: staff 数据带（col 0-1）
        CellBinding staffName = listCol(new CellRef("sheet1", 1, 0), new FieldRef("d_staff", "name"));
        CellBinding staffUnit = listCol(new CellRef("sheet1", 1, 1), new FieldRef("d_staff", "unit"));
        // Row 1: products 数据带（col 2-3）
        CellBinding prodName = listCol(new CellRef("sheet1", 1, 2), new FieldRef("d_prod", "name"));
        CellBinding prodPrice = listCol(new CellRef("sheet1", 1, 3), new FieldRef("d_prod", "price"));
        // Row 2: 员工总数（single 聚合，应被 shift 到 row 2 + max(3,2) = 5）
        CellBinding totalLabel = label(2, 0, "员工总数");
        CellBinding totalCount = CellBinding.builder()
                .cell(new CellRef("sheet1", 2, 1))
                .value(new Value.Aggregate("COUNT", new Value.FieldValue(new FieldRef("d_staff", "name"))))
                .build();

        Report report = report("r_indep", dm,
                List.of(h1, h2, h3, h4, staffName, staffUnit, prodName, prodPrice, totalLabel, totalCount),
                List.of(), List.of());

        Sheet sheet = run(dm, report, Map.of(), "independent-bands");

        // 表头
        assertEquals("姓名", text(sheet, 0, 0));
        assertEquals("单位", text(sheet, 0, 1));
        assertEquals("商品名", text(sheet, 0, 2));
        assertEquals("价格", text(sheet, 0, 3));

        // staff 数据（4 行：张三、李四、王五、赵六）
        assertEquals("张三", text(sheet, 1, 0));
        assertEquals("研发中心", text(sheet, 1, 1));
        assertEquals("赵六", text(sheet, 4, 0));

        // products 数据（3 行：苹果、香蕉、橙子）
        assertEquals("苹果", text(sheet, 1, 2));
        assertEquals(5.0, number(sheet, 1, 3), 0.0001);
        assertEquals("橙子", text(sheet, 3, 2));

        // products 只有 3 行，第 4 行（row 4）col 2-3 应为空
        assertNull(findCell(sheet, 4, 2), "products 只有 3 行，第 4 行应为空");
        assertNull(findCell(sheet, 4, 3), "products 只有 3 行，第 4 行应为空");

        // 员工总数 single 从 row 2 下移 max(4-1, 3-1) = 3 → row 5
        assertEquals("员工总数", text(sheet, 5, 0));
        assertEquals(4.0, number(sheet, 5, 1), 0.0001);
    }

    // ============================================================
    // 7b. 并列独立数据带 + 各带各自的行汇总（验证汇总作用域不串扰）
    // ============================================================

    @Test
    @DisplayName("并列各带行汇总：staff 总计落 col0-1/row5，products 总计落 col2-3/row4，互不串扰")
    void independentBandsEachWithSummary() throws Exception {
        // 两个数据集，无 Relationship —— 与 independentBands 同构
        DataSource srcStaff = csv("ds_staff", "/data/staff.csv");
        Dataset staff = TableDataset.builder().id("d_staff").datasourceId("ds_staff").sourceTable("staff.csv")
                .fields(List.of(
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("unit").dataType(DataType.STRING).build()))
                .build();
        DataSource srcProd = csv("ds_prod", "/data/products.csv");
        Dataset prod = TableDataset.builder().id("d_prod").datasourceId("ds_prod").sourceTable("products.csv")
                .fields(List.of(
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("price").dataType(DataType.NUMBER).build()))
                .build();
        DataModel dm = DataModel.builder().id("dm_indep2").name("独立模型-双汇总")
                .datasources(List.of(srcStaff, srcProd))
                .datasets(List.of(staff, prod))
                .relationships(List.of())
                .build();

        // Row 0: 表头
        CellBinding h1 = label(0, 0, "姓名");
        CellBinding h2 = label(0, 1, "单位");
        CellBinding h3 = label(0, 2, "商品名");
        CellBinding h4 = label(0, 3, "价格");
        // Row 1: staff 带（col 0-1），products 带（col 2-3）
        CellBinding staffName = listCol(new CellRef("sheet1", 1, 0), new FieldRef("d_staff", "name"));
        CellBinding staffUnit = listCol(new CellRef("sheet1", 1, 1), new FieldRef("d_staff", "unit"));
        CellBinding prodName = listCol(new CellRef("sheet1", 1, 2), new FieldRef("d_prod", "name"));
        CellBinding prodPrice = listCol(new CellRef("sheet1", 1, 3), new FieldRef("d_prod", "price"));

        // 各带各自一行总计：staff 落 col 0-1，products 落 col 2-3
        SummaryRow staffTotal = SummaryRow.builder().groupBy(null).fromColumn(0).toColumn(1).cells(List.of(
                SummaryCell.label(0, "员工合计"),
                SummaryCell.agg(1, new FieldRef("d_staff", "name"), "COUNT"))).build();
        SummaryRow prodTotal = SummaryRow.builder().groupBy(null).fromColumn(2).toColumn(3).cells(List.of(
                SummaryCell.label(2, "商品合计"),
                SummaryCell.agg(3, new FieldRef("d_prod", "price"), "SUM"))).build();

        Report report = report("r_indep2", dm,
                List.of(h1, h2, h3, h4, staffName, staffUnit, prodName, prodPrice),
                List.of(), List.of(), List.of(staffTotal, prodTotal));

        Sheet sheet = run(dm, report, Map.of(), "independent-bands-summary");

        // 表头
        assertEquals("姓名", text(sheet, 0, 0));
        assertEquals("价格", text(sheet, 0, 3));

        // staff 明细：4 行（row 1-4），第 4 行为赵六/市场中心
        assertEquals("张三", text(sheet, 1, 0));
        assertEquals("赵六", text(sheet, 4, 0));
        assertEquals("市场中心", text(sheet, 4, 1));

        // staff 总计：seq = 4 明细 + 1 总计 → 落在 row 5（col 0-1）
        assertEquals("员工合计", text(sheet, 5, 0));
        assertEquals(4.0, number(sheet, 5, 1), 0.0001);

        // products 明细：3 行（row 1-3）
        assertEquals("苹果", text(sheet, 1, 2));
        assertEquals("橙子", text(sheet, 3, 2));

        // products 总计：seq = 3 明细 + 1 总计 → 落在 row 4（col 2-3），SUM=5+3+4=12
        assertEquals("商品合计", text(sheet, 4, 2));
        assertEquals(12.0, number(sheet, 4, 3), 0.0001);

        // ---- 隔离验证（旧实现会因总计广播而失败）----
        // staff 总计行（row 5）右侧无内容：products 总计不应被广播到这里
        assertNull(findCell(sheet, 5, 2), "staff 总计行右侧不应出现 products 汇总");
        assertNull(findCell(sheet, 5, 3), "staff 总计行右侧不应出现 products 汇总");
        // products 总计行（row 4）左侧是 staff 明细赵六，而非被广播的"员工合计"
        assertEquals("赵六", text(sheet, 4, 0), "products 总计行左侧应是 staff 明细，而非被广播的员工合计");
    }

    // ============================================================
    // 9. 模板 merge 下移：VERTICAL band 下方有模板合并区域，扩展后 merge 应跟随下移
    // ============================================================

    @Test
    @DisplayName("模板 merge 下移：band(row2) 下方 row3 有模板合并，3 条数据扩展后 merge 应移至 row5")
    void templateMergeShiftedAfterBandExpansion() throws Exception {
        DataSource src = csv("ds_prod", "/data/products.csv");
        Dataset prod = TableDataset.builder().id("d_prod").datasourceId("ds_prod").sourceTable("products.csv")
                .fields(List.of(
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("price").dataType(DataType.NUMBER).build()))
                .build();
        DataModel dm = DataModel.builder().id("dm_prod").name("商品模型")
                .datasources(List.of(src)).datasets(List.of(prod)).relationships(List.of()).build();

        CellBinding title = label(0, 0, "商品清单");
        CellBinding h1 = label(1, 0, "商品名");
        CellBinding h2 = label(1, 1, "价格");
        CellBinding nameCol = listCol(new CellRef("sheet1", 2, 0), new FieldRef("d_prod", "name"));
        CellBinding priceCol = listCol(new CellRef("sheet1", 2, 1), new FieldRef("d_prod", "price"));
        Report report = report("r_merge_shift", dm, List.of(title, h1, h2, nameCol, priceCol),
                List.of(), List.of());

        // 模板：标题 merge(row0, colSpan=2) + band 下方 merge(row3, colSpan=2)
        Workbook template = new Workbook();
        Sheet tplSheet = new Sheet();
        tplSheet.setId("sheet1");
        tplSheet.setName("Sheet1");
        Merge titleMerge = new Merge();
        titleMerge.setStartRow(0); titleMerge.setStartCol(0);
        titleMerge.setRowSpan(1); titleMerge.setColSpan(2);
        Merge belowBandMerge = new Merge();
        belowBandMerge.setStartRow(3); belowBandMerge.setStartCol(0);
        belowBandMerge.setRowSpan(1); belowBandMerge.setColSpan(2);
        tplSheet.setMerges(List.of(titleMerge, belowBandMerge));
        template.setSheets(List.of(tplSheet));

        Workbook workbook = renderer.render(dm, report, new ParamContext(Map.of()), template);
        Sheet sheet = workbook.getSheets().get(0);

        // 数据 3 行：row 2/3/4
        assertEquals("苹果", text(sheet, 2, 0));
        assertEquals("香蕉", text(sheet, 3, 0));
        assertEquals("橙子", text(sheet, 4, 0));

        // 标题 merge 保持不动（row 0 < bandBase 2，不满足 >= bandBase + 1）
        assertTrue(sheet.getMerges().stream().anyMatch(m ->
                        m.getStartRow() == 0 && m.getStartCol() == 0 && m.getColSpan() == 2),
                "标题 merge 应保持原位");

        // band 下方 merge：原 row 3 → 应移至 row 5（shift = 3 - 1 = 2）
        assertTrue(sheet.getMerges().stream().anyMatch(m ->
                        m.getStartRow() == 5 && m.getStartCol() == 0 && m.getColSpan() == 2),
                "band 下方的模板 merge 应下移至 row 5");

        // 原位置的 merge 不应再存在
        assertTrue(sheet.getMerges().stream().noneMatch(m ->
                        m.getStartRow() == 3 && m.getStartCol() == 0 && m.getColSpan() == 2),
                "原 row 3 的 merge 不应再存在");
    }

    @Test
    @DisplayName("汇总行模板 merge：band(row0) + 汇总(row1)，merge(row1,colSpan=2) 应跟随汇总行下移")
    void summaryTemplateMergeFollowsSummary() throws Exception {
        DataSource src = csv("ds_prod", "/data/products.csv");
        Dataset prod = TableDataset.builder().id("d_prod").datasourceId("ds_prod").sourceTable("products.csv")
                .fields(List.of(
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("price").dataType(DataType.NUMBER).build()))
                .build();
        DataModel dm = DataModel.builder().id("dm_prod").name("商品模型")
                .datasources(List.of(src)).datasets(List.of(prod)).relationships(List.of()).build();

        // band at row 0: name(VERT) + price(VERT)
        CellBinding nameCol = listCol(new CellRef("sheet1", 0, 0), new FieldRef("d_prod", "name"));
        CellBinding priceCol = listCol(new CellRef("sheet1", 0, 1), new FieldRef("d_prod", "price"));
        // summary at template row 1: label("合计") + SUM(price)
        SummaryRow total = SummaryRow.builder()
                .row(1)
                .groupBy(null).fromColumn(0).toColumn(1)
                .cells(List.of(
                        SummaryCell.label(0, "合计"),
                        SummaryCell.agg(1, new FieldRef("d_prod", "price"), "SUM")))
                .build();
        Report report = report("r_summary_merge", dm, List.of(nameCol, priceCol),
                List.of(), List.of(), List.of(total));

        // 模板：merge at row 1, colSpan 2（为汇总行的"合计"合并前两列）
        Workbook template = new Workbook();
        Sheet tplSheet = new Sheet();
        tplSheet.setId("sheet1");
        tplSheet.setName("Sheet1");
        Merge summaryMerge = new Merge();
        summaryMerge.setStartRow(1); summaryMerge.setStartCol(0);
        summaryMerge.setRowSpan(1); summaryMerge.setColSpan(2);
        tplSheet.setMerges(List.of(summaryMerge));
        template.setSheets(List.of(tplSheet));

        Workbook workbook = renderer.render(dm, report, new ParamContext(Map.of()), template);
        Sheet sheet = workbook.getSheets().get(0);

        // 数据 3 行：row 0/1/2
        assertEquals("苹果", text(sheet, 0, 0));
        assertEquals("香蕉", text(sheet, 1, 0));
        assertEquals("橙子", text(sheet, 2, 0));

        // 汇总行在 row 3（bandBase=0, shift=2, 模板 row 1 → 输出 row 3）
        assertEquals("合计", text(sheet, 3, 0));
        assertEquals(12.0, number(sheet, 3, 1), 0.0001);

        // 汇总行的 merge 应跟随汇总行移至 row 3（而非停留在 row 1）
        assertTrue(sheet.getMerges().stream().anyMatch(m ->
                        m.getStartRow() == 3 && m.getStartCol() == 0 && m.getColSpan() == 2),
                "汇总行的模板 merge 应跟随汇总行下移至 row 3");

        // 原位置的 merge 不应再存在
        assertTrue(sheet.getMerges().stream().noneMatch(m ->
                        m.getStartRow() == 1 && m.getStartCol() == 0 && m.getColSpan() == 2),
                "原 row 1 的 merge 不应再存在（避免数据区内出现多余合并）");
    }

    @Test
    @DisplayName("静态页脚下移：band(row0)+汇总(row1)+静态'你好'(row2)，3 条数据后页脚应落 row4 不丢失")
    void staticFooterFollowsBandExpansion() throws Exception {
        DataSource src = csv("ds_prod", "/data/products.csv");
        Dataset prod = TableDataset.builder().id("d_prod").datasourceId("ds_prod").sourceTable("products.csv")
                .fields(List.of(
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("price").dataType(DataType.NUMBER).build()))
                .build();
        DataModel dm = DataModel.builder().id("dm_prod").name("商品模型")
                .datasources(List.of(src)).datasets(List.of(prod)).relationships(List.of()).build();

        // band 在 row 0：name/price（VERTICAL LIST）
        CellBinding nameCol = listCol(new CellRef("sheet1", 0, 0), new FieldRef("d_prod", "name"));
        CellBinding priceCol = listCol(new CellRef("sheet1", 0, 1), new FieldRef("d_prod", "price"));
        // 汇总在设计 row 1：合计 + SUM(price)
        SummaryRow total = SummaryRow.builder().row(1).groupBy(null).fromColumn(0).toColumn(1).cells(List.of(
                SummaryCell.label(0, "合计"),
                SummaryCell.agg(1, new FieldRef("d_prod", "price"), "SUM"))).build();
        Report report = report("r_footer", dm, List.of(nameCol, priceCol),
                List.of(), List.of(), List.of(total));

        // 模板：仅静态页脚"你好"在 row 2（无任何绑定）
        Workbook template = new Workbook();
        Sheet tplSheet = new Sheet();
        tplSheet.setId("sheet1");
        tplSheet.setName("Sheet1");
        Cell footer = new Cell();
        footer.setRow(2);
        footer.setCol(0);
        footer.setValue(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode("你好"));
        tplSheet.setCells(List.of(footer));
        template.setSheets(List.of(tplSheet));

        Workbook workbook = renderer.render(dm, report, new ParamContext(Map.of()), template);
        Sheet sheet = workbook.getSheets().get(0);

        // 数据 3 行：row 0/1/2
        assertEquals("苹果", text(sheet, 0, 0));
        assertEquals("香蕉", text(sheet, 1, 0));
        assertEquals("橙子", text(sheet, 2, 0));
        // 汇总落 row 3（band 扩展插入 2 行：3 数据 - 1 声明行）
        assertEquals("合计", text(sheet, 3, 0));
        assertEquals(12.0, number(sheet, 3, 1), 0.0001);
        // 静态页脚"你好"应下移到 row 4（汇总下方），而非被数据覆盖丢失
        assertEquals("你好", text(sheet, 4, 0));
        // 原 row 2 此时是数据"橙子"，不应仍是"你好"
        assertEquals("橙子", text(sheet, 2, 0));
    }

    @Test
    @DisplayName("带声明格错位：name@(0,0) 与 price@(11,1) 同源对齐成一条带，B12 占位不残留")
    void bandDeclCellsAtDifferentRowsAlignAndNoStalePlaceholder() throws Exception {
        DataSource src = csv("ds_prod", "/data/products.csv");
        Dataset prod = TableDataset.builder().id("d_prod").datasourceId("ds_prod").sourceTable("products.csv")
                .fields(List.of(
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("price").dataType(DataType.NUMBER).build()))
                .build();
        DataModel dm = DataModel.builder().id("dm_prod").name("商品模型")
                .datasources(List.of(src)).datasets(List.of(prod)).relationships(List.of()).build();

        // name 声明在 (0,0)，price 声明在 (11,1)（用户随意拖放，行不一致）
        CellBinding nameCol = listCol(new CellRef("sheet1", 0, 0), new FieldRef("d_prod", "name"));
        CellBinding priceCol = listCol(new CellRef("sheet1", 11, 1), new FieldRef("d_prod", "price"));
        Report report = report("r_misaligned", dm, List.of(nameCol, priceCol), List.of(), List.of());

        // 模板：两个声明格各带占位文字
        Workbook template = new Workbook();
        Sheet tplSheet = new Sheet();
        tplSheet.setId("sheet1");
        tplSheet.setName("Sheet1");
        tplSheet.setCells(List.of(
                textProto(0, 0, "商品名"),
                textProto(11, 1, "价格")));
        template.setSheets(List.of(tplSheet));

        Workbook workbook = renderer.render(dm, report, new ParamContext(Map.of()), template);
        Sheet sheet = workbook.getSheets().get(0);

        // 同源字段对齐成一条带，从 bandBase=min(0,11)=0 起逐行：name@col0 / price@col1 同行
        assertEquals("苹果", text(sheet, 0, 0));
        assertEquals(5.0, number(sheet, 0, 1), 0.0001);
        assertEquals("香蕉", text(sheet, 1, 0));
        assertEquals(3.0, number(sheet, 1, 1), 0.0001);
        assertEquals("橙子", text(sheet, 2, 0));

        // price 声明格 (11,1) 的占位文字"价格"不应残留（数据只到 row2，未覆盖 row11）
        assertNull(findCell(sheet, 11, 1), "price 声明格 B12 的占位文字应被清除，不残留");
    }

    @Test
    @DisplayName("显式独立带：name@(0,0) 对齐带 + price@(2,2) 独立带，各自从声明行展开互不对齐")
    void explicitIndependentBandStaggers() throws Exception {
        DataSource src = csv("ds_prod", "/data/products.csv");
        Dataset prod = TableDataset.builder().id("d_prod").datasourceId("ds_prod").sourceTable("products.csv")
                .fields(List.of(
                        Field.builder().name("name").dataType(DataType.STRING).build(),
                        Field.builder().name("price").dataType(DataType.NUMBER).build()))
                .build();
        DataModel dm = DataModel.builder().id("dm_prod").name("商品模型")
                .datasources(List.of(src)).datasets(List.of(prod)).relationships(List.of()).build();

        // name 普通对齐带，从 row0 起
        CellBinding nameCol = listCol(new CellRef("sheet1", 0, 0), new FieldRef("d_prod", "name"));
        // price 显式独立带，从它自己的声明行 row2 起，不与 name 对齐
        CellBinding priceCol = CellBinding.builder()
                .cell(new CellRef("sheet1", 2, 2)).value(new Value.FieldValue(new FieldRef("d_prod", "price")))
                .expansion(Expansion.VERTICAL).expandMode(ExpandMode.LIST).independent(true).build();
        Report report = report("r_indep_explicit", dm, List.of(nameCol, priceCol), List.of(), List.of());

        Sheet sheet = run(dm, report, Map.of(), "independent-explicit");

        // name 从 row0：苹果/香蕉/橙子
        assertEquals("苹果", text(sheet, 0, 0));
        assertEquals("香蕉", text(sheet, 1, 0));
        assertEquals("橙子", text(sheet, 2, 0));
        // price 独立带从 row2（声明行），与 name 错位：row2=5, row3=3, row4=4
        assertEquals(5.0, number(sheet, 2, 2), 0.0001);
        assertEquals(3.0, number(sheet, 3, 2), 0.0001);
        assertEquals(4.0, number(sheet, 4, 2), 0.0001);
        // price 不应出现在 row0/row1（证明没被拉到对齐带的基准行 0）
        assertNull(findCell(sheet, 0, 2), "独立带 price 不应对齐到 row0");
        assertNull(findCell(sheet, 1, 2), "独立带 price 不应对齐到 row1");
    }

    private static Cell textProto(int row, int col, String value) {
        Cell c = new Cell();
        c.setRow(row);
        c.setCol(col);
        c.setValue(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode(value));
        return c;
    }

    // ============================================================
    // ---- 公共构造 / 运行 / 断言辅助 ----
    // ============================================================

    private static DataSource csv(String id, String path) {
        return DataSource.builder().id(id).name(id).type(DataSourceType.CSV)
                .config(Map.of("path", path)).build();
    }

    /** 静态文本格（标题/表头） */
    private static CellBinding label(int row, int col, String text) {
        return CellBinding.builder().cell(new CellRef("sheet1", row, col)).value(Templates.parse(text)).build();
    }

    private static CellBinding listCol(CellRef cell, FieldRef field) {
        return CellBinding.builder().cell(cell).value(new Value.FieldValue(field))
                .expansion(Expansion.VERTICAL).expandMode(ExpandMode.LIST).build();
    }

    /** 分组列（去重 + 合并），可指定父格构成层级 */
    private static CellBinding groupMerge(CellRef cell, FieldRef field, CellRef parent) {
        return CellBinding.builder().cell(cell).value(new Value.FieldValue(field))
                .expansion(Expansion.VERTICAL).expandMode(ExpandMode.GROUP).mergeRepeated(true)
                .parentCell(parent).build();
    }

    /** 薪资条块内数据格：取 d_sal 的某字段，按 emp_id = 循环当前员工 id 过滤 */
    private static CellBinding salaryCell(int row, int col, String field) {
        return CellBinding.builder().cell(new CellRef("sheet1", row, col))
                .value(new Value.FieldValue(new FieldRef("d_sal", field))).expansion(Expansion.NONE)
                .conditions(List.of(cond(new Value.FieldValue(new FieldRef("d_sal", "emp_id")), CompareOperator.EQ,
                        new Value.LoopFieldValue("loop_emp", "id")))).build();
    }

    private static Condition cond(Value left, CompareOperator op, Value right) {
        return Condition.builder().left(left).operator(op).right(right).build();
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
