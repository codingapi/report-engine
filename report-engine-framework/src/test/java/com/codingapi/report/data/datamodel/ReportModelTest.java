package com.codingapi.report.data.datamodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingapi.report.data.dataset.DataType;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.data.dataset.Query;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.type.DbDataSourceType;
import com.codingapi.report.data.relation.JoinType;
import com.codingapi.report.data.relation.RelationOrigin;
import com.codingapi.report.data.relation.Relationship;
import com.codingapi.report.expression.Templates;
import com.codingapi.report.expression.Value;
import com.codingapi.report.operator.condition.CompareOperator;
import com.codingapi.report.operator.condition.Condition;
import com.codingapi.report.param.ParamSource;
import com.codingapi.report.param.Parameter;
import com.codingapi.report.core.Report;
import com.codingapi.report.core.grid.CellBinding;
import com.codingapi.report.core.grid.CellRef;
import com.codingapi.report.core.grid.ExpandMode;
import com.codingapi.report.core.grid.Expansion;
import com.codingapi.report.core.grid.LoopBlock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 用典型场景验证报表模型，同时充当模型设计的"可执行文档"。
 *
 * <p>核心结构：可复用的 {@link DataModel}（连接/数据集/关系）+ 引用它的多个 {@link Report} （参数/格子/循环）。覆盖：
 *
 * <ol>
 *   <li>DataModel 复用、跨源关系
 *   <li>部门报表 —— 显式外部参数
 *   <li>薪资条 —— 循环驱动 Query、LoopField 免登记、循环字段发现
 *   <li>成绩交叉表 —— 行 × 列 + 参数化标题文本
 *   <li>多级分组统计表 —— 父格链 + GROUP + 合并
 *   <li>文本格 vs 字段格的密封区分
 * </ol>
 */
class ReportModelTest {

    // ============================================================
    // 1. DataModel 可复用
    // ============================================================

    @Test
    @DisplayName("DataModel 复用：部门报表与薪资条引用同一个数据模型，关系只维护一次")
    void dataModel_reusableAcrossReports() {
        DataModel hr = hrDataModel();
        Report deptReport = departmentReport(hr);
        Report payslip = payslipReport(hr);

        assertEquals(hr.getId(), deptReport.getDataModelId());
        assertEquals(hr.getId(), payslip.getDataModelId());

        assertEquals(1, hr.getRelationships().size());
        assertTrue(
                deptReport.getExtraRelationships() == null
                        || deptReport.getExtraRelationships().isEmpty());
        assertTrue(
                payslip.getExtraRelationships() == null
                        || payslip.getExtraRelationships().isEmpty());
    }

    @Test
    @DisplayName("跨源关系建在 DataModel 上：员工与薪资来自不同连接")
    void dataModel_crossSourceRelationship() {
        DataModel hr = hrDataModel();
        Relationship rel = hr.getRelationships().get(0);

        assertEquals(JoinType.INNER, rel.getJoinType());
        assertEquals(RelationOrigin.MANUAL, rel.getOrigin(), "跨库关系需手动连");

        TableDataset left = (TableDataset) findDataset(hr, rel.getLeft().datasetId());
        TableDataset right = (TableDataset) findDataset(hr, rel.getRight().datasetId());
        assertNotEquals(left.getDatasourceId(), right.getDatasourceId(), "薪资库与人事库是两个不同连接");
        // 两个连接都是 DB 类型（JDBC），不区分厂商
        assertEquals(
                "DB", findDatasource(hr, left.getDatasourceId()).getType().type());
        assertEquals(
                "DB", findDatasource(hr, right.getDatasourceId()).getType().type());
    }

    // ============================================================
    // 2. 部门报表：显式参数
    // ============================================================

    @Test
    @DisplayName("部门报表：显式参数 deptId 是运行时输入契约，条件用 :deptId 引用而非写死")
    void departmentReport_externalParam() {
        Report report = departmentReport(hrDataModel());

        Parameter deptId = findParam(report, "deptId");
        ParamSource.External ext = assertInstanceOf(ParamSource.External.class, deptId.getSource());
        assertTrue(ext.required(), "deptId 必填");
        assertEquals(Set.of("deptId"), externalParamNames(report));

        CellBinding nameCell = findFieldCell(report, new FieldRef("d_emp", "name"));
        assertEquals(Expansion.VERTICAL, nameCell.getExpansion());
        Value.ParamValue ref =
                assertInstanceOf(
                        Value.ParamValue.class, nameCell.getConditions().get(0).getRight());
        assertEquals("deptId", ref.name());
    }

    // ============================================================
    // 3. 薪资条：循环驱动 Query、LoopField 免登记、循环字段发现
    // ============================================================

    @Test
    @DisplayName("薪资条：循环驱动是 Query（数据集+过滤），逐人迭代")
    void payslip_loopDrivenByQuery() {
        Report report = payslipReport(hrDataModel());
        Query q = report.getLoopBlocks().get(0).getSource();

        assertEquals("d_emp", q.getDatasetId());
        assertEquals(1, q.getFilters().size());
        assertEquals(
                new Value.FieldValue(new FieldRef("d_emp", "status")),
                q.getFilters().get(0).getLeft());
        assertTrue(q.getGroupBy() == null || q.getGroupBy().isEmpty());
    }

    @Test
    @DisplayName("薪资条：块内格子用 LoopField 直接引用循环当前行，无需登记任何参数")
    void payslip_cellReferencesLoopFieldWithoutRegistration() {
        Report report = payslipReport(hrDataModel());
        LoopBlock loop = report.getLoopBlocks().get(0);

        CellBinding baseCell = findFieldCell(report, new FieldRef("d_salary", "base"));
        Value.LoopFieldValue ref =
                assertInstanceOf(
                        Value.LoopFieldValue.class,
                        baseCell.getConditions().get(0).getRight(),
                        "应为循环字段引用而非预登记参数");
        assertEquals(loop.getId(), ref.loopBlockId());
        assertEquals("id", ref.field());

        // 参数注册表里没有为循环值登记任何参数
        assertFalse(report.getParameters().stream().anyMatch(p -> p.getName().equals("empId")));
    }

    @Test
    @DisplayName("循环字段发现：块内可引用 = 报表参数 ∪ 驱动数据集字段；块外看不到循环字段")
    void loopField_discoverableFromDrivingDataset() {
        DataModel hr = hrDataModel();
        Report report = payslipReport(hr);
        LoopBlock loop = report.getLoopBlocks().get(0);

        CellRef insideLoop = findFieldCell(report, new FieldRef("d_salary", "base")).getCell();
        Set<String> sources = availableValueSources(hr, report, insideLoop);
        assertTrue(sources.contains(":deptId"));
        assertTrue(sources.contains(loop.getId() + ".id"));
        assertTrue(sources.contains(loop.getId() + ".name"));

        Set<String> outside = availableValueSources(hr, report, new CellRef("sheet1", 50, 50));
        assertTrue(outside.contains(":deptId"));
        assertFalse(outside.contains(loop.getId() + ".id"));
    }

    // ============================================================
    // 4. 成绩交叉表 + 参数化标题文本
    // ============================================================

    @Test
    @DisplayName("交叉表：纵向(行) + 横向(列) + 交叉格聚合，正交组合成矩阵报表")
    void crossTab_rowAndColumnExpansion() {
        Report report = scoreCrossTab(eduDataModel());

        assertEquals(
                Expansion.VERTICAL,
                findFieldCell(report, new FieldRef("d_score", "student")).getExpansion());
        assertEquals(
                Expansion.HORIZONTAL,
                findFieldCell(report, new FieldRef("d_score", "subject")).getExpansion());

        CellBinding scoreCell = findFieldCell(report, new FieldRef("d_score", "score"));
        assertEquals(Expansion.NONE, scoreCell.getExpansion());
        Value.Aggregate agg = assertInstanceOf(Value.Aggregate.class, scoreCell.getValue());
        assertEquals("AVG", agg.aggregation());
    }

    @Test
    @DisplayName("标题是文本格：纯文字不引用数据，但可含参数占位 ${year}，且占位符都已声明为参数")
    void textCell_titleWithParameter() {
        Report report = scoreCrossTab(eduDataModel());

        // 标题是文本格：值为 Template（文本插值），而非字段/聚合
        CellBinding title =
                report.getCellBindings().stream()
                        .filter(b -> b.getValue() instanceof Value.Template)
                        .findFirst()
                        .orElseThrow();
        Value.Template tpl = (Value.Template) title.getValue();
        // 模板含静态文本片段「年度成绩交叉表」
        assertTrue(
                tpl.parts().stream()
                        .anyMatch(
                                p ->
                                        p instanceof Value.Template.Text t
                                                && t.text().equals("年度成绩交叉表")));

        // 文本里的参数占位符都能在报表参数里找到（与条件共用同一套参数系统）
        Set<String> placeholders = templatePlaceholders(tpl);
        assertEquals(Set.of("year"), placeholders);
        assertTrue(externalParamNames(report).containsAll(placeholders));
    }

    // ============================================================
    // 5. 多级分组统计表
    // ============================================================

    @Test
    @DisplayName("多级分组：单位→部门→明细 用父格链串联，分组列 GROUP+合并、明细列 LIST")
    void multiLevelGrouping_parentChainGroupMerge() {
        Report report = orgStatReport(statDataModel());

        CellBinding unit = findFieldCell(report, new FieldRef("d_stat", "unit"));
        CellBinding dept = findFieldCell(report, new FieldRef("d_stat", "dept"));
        CellBinding detail = findFieldCell(report, new FieldRef("d_stat", "amount"));

        assertEquals(ExpandMode.GROUP, unit.getExpandMode());
        assertTrue(unit.isMergeRepeated());
        assertEquals(ExpandMode.GROUP, dept.getExpandMode());
        assertTrue(dept.isMergeRepeated());

        assertEquals(ExpandMode.LIST, detail.getExpandMode());
        assertFalse(detail.isMergeRepeated());

        assertEquals(dept.getCell(), detail.getParentCell());
        assertEquals(unit.getCell(), dept.getParentCell());
        assertNull(unit.getParentCell());
    }

    // ============================================================
    // ---- 数据模型（可复用语义层）----
    // ============================================================

    /** 人事数据模型：员工(人事库) + 薪资(薪资库) + 跨库关系。被部门报表与薪资条共享。 */
    private static DataModel hrDataModel() {
        DataSource hrDb =
                DataSource.builder().id("ds_hr").name("人事库").type(new DbDataSourceType(null, null)).build();
        DataSource payDb =
                DataSource.builder().id("ds_pay").name("薪资库").type(new DbDataSourceType(null, null)).build();

        Dataset emp =
                TableDataset.builder()
                        .id("d_emp")
                        .datasource(hrDb).datasourceId("ds_hr")
                        .sourceTable("employee")
                        .alias("员工")
                        .fields(
                                List.of(
                                        Field.builder()
                                                .name("id")
                                                .alias("ID")
                                                .dataType(DataType.NUMBER)
                                                .primaryKey(true)
                                                .build(),
                                        Field.builder()
                                                .name("name")
                                                .alias("姓名")
                                                .dataType(DataType.STRING)
                                                .build(),
                                        Field.builder()
                                                .name("dept_id")
                                                .alias("部门")
                                                .dataType(DataType.NUMBER)
                                                .build(),
                                        Field.builder()
                                                .name("status")
                                                .alias("状态")
                                                .dataType(DataType.STRING)
                                                .build()))
                        .build();

        Dataset salary =
                TableDataset.builder()
                        .id("d_salary")
                        .datasource(payDb).datasourceId("ds_pay")
                        .sourceTable("salary")
                        .alias("薪资")
                        .fields(
                                List.of(
                                        Field.builder()
                                                .name("emp_id")
                                                .alias("员工")
                                                .dataType(DataType.NUMBER)
                                                .build(),
                                        Field.builder()
                                                .name("base")
                                                .alias("基本工资")
                                                .dataType(DataType.NUMBER)
                                                .build(),
                                        Field.builder()
                                                .name("bonus")
                                                .alias("奖金")
                                                .dataType(DataType.NUMBER)
                                                .build()))
                        .build();

        Relationship rel =
                Relationship.builder()
                        .id("rel_emp_salary")
                        .left(new FieldRef("d_salary", "emp_id"))
                        .right(new FieldRef("d_emp", "id"))
                        .joinType(JoinType.INNER)
                        .origin(RelationOrigin.MANUAL)
                        .build();

        return DataModel.builder()
                .id("dm_hr")
                .name("人事数据模型")
                .datasets(List.of(emp, salary))
                .relationships(List.of(rel))
                .build();
    }

    /** 教务数据模型：成绩宽表（单数据集，无需关系） */
    private static DataModel eduDataModel() {
        DataSource edu =
                DataSource.builder().id("ds_edu").name("教务库").type(new DbDataSourceType(null, null)).build();
        Dataset score =
                TableDataset.builder()
                        .id("d_score")
                        .datasource(edu).datasourceId("ds_edu")
                        .sourceTable("score_view")
                        .alias("成绩")
                        .fields(
                                List.of(
                                        Field.builder()
                                                .name("student")
                                                .alias("学生")
                                                .dataType(DataType.STRING)
                                                .build(),
                                        Field.builder()
                                                .name("subject")
                                                .alias("科目")
                                                .dataType(DataType.STRING)
                                                .build(),
                                        Field.builder()
                                                .name("score")
                                                .alias("分数")
                                                .dataType(DataType.NUMBER)
                                                .build()))
                        .build();
        return DataModel.builder()
                .id("dm_edu")
                .name("教务数据模型")
                .datasets(List.of(score))
                .relationships(List.of())
                .build();
    }

    /** 统计数据模型：单位/部门/明细 宽表 */
    private static DataModel statDataModel() {
        DataSource db =
                DataSource.builder().id("ds_stat").name("统计库").type(new DbDataSourceType(null, null)).build();
        Dataset stat =
                TableDataset.builder()
                        .id("d_stat")
                        .datasource(db).datasourceId("ds_stat")
                        .sourceTable("stat_view")
                        .alias("统计")
                        .fields(
                                List.of(
                                        Field.builder()
                                                .name("unit")
                                                .alias("单位")
                                                .dataType(DataType.STRING)
                                                .build(),
                                        Field.builder()
                                                .name("dept")
                                                .alias("部门")
                                                .dataType(DataType.STRING)
                                                .build(),
                                        Field.builder()
                                                .name("name")
                                                .alias("姓名")
                                                .dataType(DataType.STRING)
                                                .build(),
                                        Field.builder()
                                                .name("amount")
                                                .alias("金额")
                                                .dataType(DataType.NUMBER)
                                                .build()))
                        .build();
        return DataModel.builder()
                .id("dm_stat")
                .name("统计数据模型")
                .datasets(List.of(stat))
                .relationships(List.of())
                .build();
    }

    // ============================================================
    // ---- 报表（引用数据模型）----
    // ============================================================

    /** 部门报表：员工明细列表，按运行时传入的 deptId 过滤 */
    private static Report departmentReport(DataModel dm) {
        Parameter deptId =
                Parameter.builder()
                        .name("deptId")
                        .dataType(DataType.NUMBER)
                        .source(new ParamSource.External(true, null))
                        .build();

        CellBinding nameCell =
                CellBinding.builder()
                        .cell(new CellRef("sheet1", 1, 0))
                        .value(new Value.FieldValue(new FieldRef("d_emp", "name")))
                        .expansion(Expansion.VERTICAL)
                        .expandMode(ExpandMode.LIST)
                        .conditions(
                                List.of(
                                        Condition.builder()
                                                .left(
                                                        new Value.FieldValue(
                                                                new FieldRef("d_emp", "dept_id")))
                                                .operator(CompareOperator.EQ)
                                                .right(new Value.ParamValue("deptId"))
                                                .build()))
                        .build();

        return Report.builder()
                .id("r_dept")
                .name("部门员工报表")
                .dataModelId(dm.getId())
                .templateId("tpl_dept")
                .parameters(List.of(deptId))
                .cellBindings(List.<CellBinding>of(nameCell))
                .loopBlocks(List.of())
                .build();
    }

    /** 薪资条：循环按在职员工迭代，每张条只查当前 empId 的薪资 */
    private static Report payslipReport(DataModel dm) {
        LoopBlock loop =
                LoopBlock.builder()
                        .id("loop_emp")
                        .label("按员工循环")
                        .start(new CellRef("sheet1", 0, 0))
                        .end(new CellRef("sheet1", 3, 2))
                        .source(
                                Query.builder()
                                        .datasetId("d_emp")
                                        .filters(
                                                List.of(
                                                        Condition.builder()
                                                                .left(
                                                                        new Value.FieldValue(
                                                                                new FieldRef(
                                                                                        "d_emp",
                                                                                        "status")))
                                                                .operator(CompareOperator.EQ)
                                                                .right(new Value.Literal("在职"))
                                                                .build()))
                                        .groupBy(List.of())
                                        .build())
                        .build();

        Parameter deptId =
                Parameter.builder()
                        .name("deptId")
                        .dataType(DataType.NUMBER)
                        .source(new ParamSource.External(false, null))
                        .build();

        CellBinding nameCell =
                CellBinding.builder()
                        .cell(new CellRef("sheet1", 0, 1))
                        .value(new Value.FieldValue(new FieldRef("d_emp", "name")))
                        .expansion(Expansion.NONE)
                        .build();

        CellBinding baseCell =
                CellBinding.builder()
                        .cell(new CellRef("sheet1", 1, 1))
                        .value(new Value.FieldValue(new FieldRef("d_salary", "base")))
                        .expansion(Expansion.NONE)
                        .conditions(
                                List.of(
                                        Condition.builder()
                                                .left(
                                                        new Value.FieldValue(
                                                                new FieldRef("d_salary", "emp_id")))
                                                .operator(CompareOperator.EQ)
                                                .right(new Value.LoopFieldValue("loop_emp", "id"))
                                                .build()))
                        .build();

        return Report.builder()
                .id("r_payslip")
                .name("员工薪资条")
                .dataModelId(dm.getId())
                .templateId("tpl_payslip")
                .parameters(List.of(deptId))
                .cellBindings(List.<CellBinding>of(nameCell, baseCell))
                .loopBlocks(List.of(loop))
                .build();
    }

    /** 成绩交叉表：行=学生(纵向)、列=科目(横向)、交叉=平均分；标题含参数 ${year} */
    private static Report scoreCrossTab(DataModel dm) {
        Parameter year =
                Parameter.builder()
                        .name("year")
                        .dataType(DataType.NUMBER)
                        .source(new ParamSource.External(true, null))
                        .build();

        CellBinding title =
                CellBinding.builder()
                        .cell(new CellRef("sheet1", 0, 0))
                        .value(Templates.parse("${year}年度成绩交叉表"))
                        .build();

        CellBinding studentCell =
                CellBinding.builder()
                        .cell(new CellRef("sheet1", 2, 0))
                        .value(new Value.FieldValue(new FieldRef("d_score", "student")))
                        .expansion(Expansion.VERTICAL)
                        .expandMode(ExpandMode.GROUP)
                        .build();
        CellBinding subjectCell =
                CellBinding.builder()
                        .cell(new CellRef("sheet1", 1, 1))
                        .value(new Value.FieldValue(new FieldRef("d_score", "subject")))
                        .expansion(Expansion.HORIZONTAL)
                        .expandMode(ExpandMode.GROUP)
                        .build();
        CellBinding scoreCell =
                CellBinding.builder()
                        .cell(new CellRef("sheet1", 2, 1))
                        .value(
                                new Value.Aggregate(
                                        "AVG",
                                        new Value.FieldValue(new FieldRef("d_score", "score"))))
                        .expansion(Expansion.NONE)
                        .parentCell(new CellRef("sheet1", 2, 0))
                        .build();

        return Report.builder()
                .id("r_crosstab")
                .name("学生成绩交叉表")
                .dataModelId(dm.getId())
                .templateId("tpl_crosstab")
                .parameters(List.of(year))
                .cellBindings(List.<CellBinding>of(title, studentCell, subjectCell, scoreCell))
                .loopBlocks(List.of())
                .build();
    }

    /** 多级分组统计表：单位 → 部门 → 明细金额 */
    private static Report orgStatReport(DataModel dm) {
        CellRef unitRef = new CellRef("sheet1", 1, 0);
        CellRef deptRef = new CellRef("sheet1", 1, 1);

        CellBinding unit =
                CellBinding.builder()
                        .cell(unitRef)
                        .value(new Value.FieldValue(new FieldRef("d_stat", "unit")))
                        .expansion(Expansion.VERTICAL)
                        .expandMode(ExpandMode.GROUP)
                        .mergeRepeated(true)
                        .build();
        CellBinding dept =
                CellBinding.builder()
                        .cell(deptRef)
                        .value(new Value.FieldValue(new FieldRef("d_stat", "dept")))
                        .expansion(Expansion.VERTICAL)
                        .expandMode(ExpandMode.GROUP)
                        .mergeRepeated(true)
                        .parentCell(unitRef)
                        .build();
        CellBinding detail =
                CellBinding.builder()
                        .cell(new CellRef("sheet1", 1, 2))
                        .value(new Value.FieldValue(new FieldRef("d_stat", "amount")))
                        .expansion(Expansion.VERTICAL)
                        .expandMode(ExpandMode.LIST)
                        .mergeRepeated(false)
                        .parentCell(deptRef)
                        .build();

        return Report.builder()
                .id("r_orgstat")
                .name("单位部门统计表")
                .dataModelId(dm.getId())
                .templateId("tpl_orgstat")
                .parameters(List.of())
                .cellBindings(List.<CellBinding>of(unit, dept, detail))
                .loopBlocks(List.of())
                .build();
    }

    // ============================================================
    // ---- 辅助方法 ----
    // ============================================================

    private static Parameter findParam(Report report, String name) {
        return report.getParameters().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static Dataset findDataset(DataModel dm, String id) {
        return dm.getDatasets().stream()
                .filter(d -> d.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static DataSource findDatasource(DataModel dm, String id) {
        return dm.getDatasets().stream()
                .filter(
                        d ->
                                d instanceof TableDataset t
                                        && t.getDatasource() != null
                                        && id.equals(t.getDatasource().getId()))
                .map(d -> ((TableDataset) d).getDatasource())
                .findFirst()
                .orElseThrow();
    }

    /** 在报表里找值绑定了指定字段的 CellBinding（FieldValue 或 Aggregate(FieldValue)） */
    private static CellBinding findFieldCell(Report report, FieldRef field) {
        return report.getCellBindings().stream()
                .filter(b -> field.equals(fieldOf(b)))
                .findFirst()
                .orElseThrow();
    }

    /** 取格子值绑定的字段：FieldValue 或 Aggregate(FieldValue) 时返回 FieldRef，否则 null。 */
    private static FieldRef fieldOf(CellBinding b) {
        Value v = b.getValue();
        if (v instanceof Value.FieldValue fv) {
            return fv.ref();
        }
        if (v instanceof Value.Aggregate a && a.operand() instanceof Value.FieldValue fv) {
            return fv.ref();
        }
        return null;
    }

    /** 报表的输入契约 = 所有 External 参数名 */
    private static Set<String> externalParamNames(Report report) {
        Set<String> names = new HashSet<>();
        for (Parameter p : report.getParameters()) {
            if (p.getSource() instanceof ParamSource.External) {
                names.add(p.getName());
            }
        }
        return names;
    }

    /** 提取文本模板里的占位符名（Hole 里的 NameRef 名字） */
    private static Set<String> templatePlaceholders(Value.Template tpl) {
        Set<String> names = new HashSet<>();
        for (Value.Template.Part p : tpl.parts()) {
            if (p instanceof Value.Template.Hole h && h.value() instanceof Value.NameRef n) {
                names.add(n.name());
            }
        }
        return names;
    }

    /**
     * 计算某个格子可引用的"取值来源"，演示循环字段的免登记可发现性： 报表参数（{@code :name}） ∪ 落在循环块范围内时该循环驱动数据集的字段（{@code
     * loopId.field}）。
     */
    private static Set<String> availableValueSources(DataModel dm, Report report, CellRef cell) {
        Set<String> sources = new HashSet<>();
        for (Parameter p : report.getParameters()) {
            sources.add(":" + p.getName());
        }
        for (LoopBlock loop : report.getLoopBlocks()) {
            if (contains(loop, cell)) {
                Dataset driving = findDataset(dm, loop.getSource().getDatasetId());
                for (Field f : driving.getFields()) {
                    sources.add(loop.getId() + "." + f.getName());
                }
            }
        }
        return sources;
    }

    private static boolean contains(LoopBlock loop, CellRef cell) {
        CellRef s = loop.getStart();
        CellRef e = loop.getEnd();
        return s.sheetId().equals(cell.sheetId())
                && cell.row() >= s.row()
                && cell.row() <= e.row()
                && cell.column() >= s.column()
                && cell.column() <= e.column();
    }
}
