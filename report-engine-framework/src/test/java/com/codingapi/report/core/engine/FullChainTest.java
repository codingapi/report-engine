package com.codingapi.report.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.data.dataset.DataType;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.type.CsvDataSourceType;
import com.codingapi.report.data.datasource.extractor.CsvDataExtractor;
import com.codingapi.report.data.relation.JoinType;
import com.codingapi.report.data.relation.RelationOrigin;
import com.codingapi.report.data.relation.Relationship;
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
import com.codingapi.report.core.Report;
import com.codingapi.report.core.grid.CellBinding;
import com.codingapi.report.core.grid.CellRef;
import com.codingapi.report.core.grid.ExpandMode;
import com.codingapi.report.core.grid.Expansion;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 全链路验证：报表配置 → 数据计算（跨 CSV 源 join + 过滤 + 聚合）→ 导出 Excel → 回读校验。
 *
 * <p>场景：学生(students.csv) join 成绩(scores.csv)，按班级参数过滤，列出"姓名/分数"， 并算平均分；标题用参数。两份 CSV 模拟"跨源"，全部计算在
 * Java 完成。
 */
class FullChainTest {

    @Test
    @DisplayName("配置→计算→导出→回读：跨 CSV join + 参数过滤 + 聚合 + 参数标题，落进 xlsx")
    void configure_compute_export_excel() {
        DataModel dm = scoreDataModel();
        Report report = scoreReport(dm);

        // 运行时参数：2025 年度、班级 10
        ParamContext ctx = new ParamContext(Map.of("year", 2025, "classId", 10));

        // —— 计算 + 渲染 ——
        ReportRenderer renderer = new ReportRenderer(List.of(new CsvDataExtractor()));
        Workbook workbook = renderer.render(dm, report, ctx);

        // —— 导出 Excel ——
        byte[] xlsx = new ExcelExporter().export(workbook);
        assertTrue(xlsx.length > 0, "应导出非空 xlsx");

        // —— 回读校验 ——
        Workbook back = new ExcelImporter().importFrom(xlsx);
        Sheet sheet = back.getSheets().get(0);

        // 标题：${year} 被替换
        assertEquals("2025年度成绩单", text(sheet, 0, 0));

        // 平均分聚合：班级10 的 张三90 + 李四80 → 85
        assertEquals(85.0, number(sheet, 1, 1), 0.0001);

        // 列表：班级10 只有 张三、李四（王五属班级20，被过滤）
        assertEquals("张三", text(sheet, 2, 0));
        assertEquals(90.0, number(sheet, 2, 1), 0.0001);
        assertEquals("李四", text(sheet, 3, 0));
        assertEquals(80.0, number(sheet, 3, 1), 0.0001);

        // 第三行不应存在（被过滤后只剩 2 行）
        assertNull(findCell(sheet, 4, 0), "王五应被班级过滤掉，不出现第三行");
    }

    // ---- 数据模型：两份 CSV + 跨源关系 ----

    private static DataModel scoreDataModel() {
        DataSource studentSrc =
                DataSource.builder()
                        .id("ds_student")
                        .name("学生CSV")
                        .type(new CsvDataSourceType(null))
                        .config(Map.of("path", "/data/students.csv"))
                        .build();
        DataSource scoreSrc =
                DataSource.builder()
                        .id("ds_score")
                        .name("成绩CSV")
                        .type(new CsvDataSourceType(null))
                        .config(Map.of("path", "/data/scores.csv"))
                        .build();

        Dataset student =
                TableDataset.builder()
                        .id("d_student")
                        .datasource(studentSrc).datasourceId("ds_student")
                        .sourceTable("students.csv")
                        .alias("学生")
                        .fields(
                                List.of(
                                        Field.builder()
                                                .name("id")
                                                .dataType(DataType.NUMBER)
                                                .primaryKey(true)
                                                .build(),
                                        Field.builder()
                                                .name("name")
                                                .dataType(DataType.STRING)
                                                .build(),
                                        Field.builder()
                                                .name("class_id")
                                                .dataType(DataType.NUMBER)
                                                .build()))
                        .build();
        Dataset score =
                TableDataset.builder()
                        .id("d_score")
                        .datasource(scoreSrc).datasourceId("ds_score")
                        .sourceTable("scores.csv")
                        .alias("成绩")
                        .fields(
                                List.of(
                                        Field.builder()
                                                .name("student_id")
                                                .dataType(DataType.NUMBER)
                                                .build(),
                                        Field.builder()
                                                .name("score")
                                                .dataType(DataType.NUMBER)
                                                .build()))
                        .build();

        // 跨源关系：成绩.student_id = 学生.id
        Relationship rel =
                Relationship.builder()
                        .id("rel_student_score")
                        .left(new FieldRef("d_score", "student_id"))
                        .right(new FieldRef("d_student", "id"))
                        .joinType(JoinType.INNER)
                        .origin(RelationOrigin.MANUAL)
                        .build();

        return DataModel.builder()
                .id("dm_score")
                .name("学生成绩模型")
                .datasets(List.of(student, score))
                .relationships(List.of(rel))
                .build();
    }

    // ---- 报表：标题 + 平均分 + 姓名/分数列表（按班级过滤）----

    private static Report scoreReport(DataModel dm) {
        Parameter year =
                Parameter.builder()
                        .name("year")
                        .dataType(DataType.NUMBER)
                        .source(new ParamSource.External(true, null))
                        .build();
        Parameter classId =
                Parameter.builder()
                        .name("classId")
                        .dataType(DataType.NUMBER)
                        .source(new ParamSource.External(true, null))
                        .build();

        CellBinding title =
                CellBinding.builder()
                        .cell(new CellRef("sheet1", 0, 0))
                        .value(Templates.parse("${year}年度成绩单"))
                        .build();

        CellBinding avg =
                CellBinding.builder()
                        .cell(new CellRef("sheet1", 1, 1))
                        .value(
                                new Value.Aggregate(
                                        "AVG",
                                        new Value.FieldValue(new FieldRef("d_score", "score"))))
                        .expansion(Expansion.NONE)
                        .build();

        // 姓名列：纵向列表，带班级过滤条件
        CellBinding nameCol =
                CellBinding.builder()
                        .cell(new CellRef("sheet1", 2, 0))
                        .value(new Value.FieldValue(new FieldRef("d_student", "name")))
                        .expansion(Expansion.VERTICAL)
                        .expandMode(ExpandMode.LIST)
                        .conditions(
                                List.of(
                                        Condition.builder()
                                                .left(
                                                        new Value.FieldValue(
                                                                new FieldRef(
                                                                        "d_student", "class_id")))
                                                .operator(CompareOperator.EQ)
                                                .right(new Value.ParamValue("classId"))
                                                .build()))
                        .build();

        // 分数列：纵向列表（与姓名列对齐同一过滤后的行集）
        CellBinding scoreCol =
                CellBinding.builder()
                        .cell(new CellRef("sheet1", 2, 1))
                        .value(new Value.FieldValue(new FieldRef("d_score", "score")))
                        .expansion(Expansion.VERTICAL)
                        .expandMode(ExpandMode.LIST)
                        .build();

        return Report.builder()
                .id("r_score")
                .name("学生成绩单")
                .dataModelId(dm.getId())
                .templateId("tpl_score")
                .parameters(List.of(year, classId))
                .cellBindings(List.<CellBinding>of(title, avg, nameCol, scoreCol))
                .loopBlocks(List.of())
                .build();
    }

    // ---- 回读辅助 ----

    private static Cell findCell(Sheet sheet, int row, int col) {
        if (sheet.getCells() == null) {
            return null;
        }
        return sheet.getCells().stream()
                .filter(c -> c.getRow() == row && c.getCol() == col)
                .findFirst()
                .orElse(null);
    }

    private static String text(Sheet sheet, int row, int col) {
        Cell c = findCell(sheet, row, col);
        return c == null ? null : c.getValue().asText();
    }

    private static Double number(Sheet sheet, int row, int col) {
        Cell c = findCell(sheet, row, col);
        return c == null ? null : c.getValue().asDouble();
    }
}
