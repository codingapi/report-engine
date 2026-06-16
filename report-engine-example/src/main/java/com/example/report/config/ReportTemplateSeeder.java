package com.example.report.config;

import com.example.report.repository.ReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预存示例报表：启动时将测试报表配置写入内存仓库。
 * <p>
 * 对应 ReportScenarioTest 中的 7 个场景。前端通过
 * {@code GET /api/report/configs/examples} 获取列表，
 * 点击即导航到 {@code /engine?id=xxx} 打开对应报表。
 * </p>
 */
@Slf4j
@Component
public class ReportTemplateSeeder {

    private static final String SHEET = "sheet1";

    private final ReportRepository repository;

    /** 预存报表的 id 集合，供 API 过滤用。 */
    private final List<String> exampleIds = new ArrayList<>();

    public ReportTemplateSeeder(ReportRepository repository) {
        this.repository = repository;
    }

    public List<String> getExampleIds() {
        return List.copyOf(exampleIds);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        seedSimpleList();
        seedMergedList();
        seedStatistics();
        seedMasterDetail();
        seedSubtotal();
        seedPayslipLoop();
        log.info("已预存 {} 个示例报表", exampleIds.size());
    }

    // ============================================================
    // 1. 简单列表：商品名 + 价格 + 合计
    // ============================================================

    private void seedSimpleList() {
        Map<String, Object> config = baseConfig("商品清单报表");
        config.put("cellBindings", List.of(
                binding(0, 0, literal("商品清单"), "NONE", "LIST"),
                binding(1, 0, literal("商品名"), "NONE", "LIST"),
                binding(1, 1, literal("价格"), "NONE", "LIST"),
                binding(2, 0, fieldValue("products", "name"), "VERTICAL", "LIST"),
                binding(2, 1, fieldValue("products", "price"), "VERTICAL", "LIST")
        ));
        config.put("summaries", List.of(
                summary(null, List.of(
                        labelCell(0, "合计"),
                        aggCell(1, "products.price", "SUM")
                ))
        ));
        config.put("template", buildWorkbook(List.of(
                cell(0, 0, "商品清单"),
                cell(1, 0, "商品名"),
                cell(1, 1, "价格")
        ), 6, 4));
        exampleIds.add(save(config));
    }

    // ============================================================
    // 2. 分组列表：销售分类合并 + 商品明细
    // ============================================================

    private void seedMergedList() {
        Map<String, Object> config = baseConfig("销售明细报表");
        config.put("cellBindings", List.of(
                binding(0, 0, literal("销售明细"), "NONE", "LIST"),
                binding(1, 0, literal("分类"), "NONE", "LIST"),
                binding(1, 1, literal("商品"), "NONE", "LIST"),
                binding(1, 2, literal("数量"), "NONE", "LIST"),
                binding(2, 0, fieldValue("sales", "category"), "VERTICAL", "GROUP", true),
                binding(2, 1, fieldValue("sales", "product"), "VERTICAL", "LIST"),
                binding(2, 2, fieldValue("sales", "qty"), "VERTICAL", "LIST")
        ));
        config.put("template", buildWorkbook(List.of(
                cell(0, 0, "销售明细"),
                cell(1, 0, "分类"),
                cell(1, 1, "商品"),
                cell(1, 2, "数量")
        ), 6, 4));
        exampleIds.add(save(config));
    }

    // ============================================================
    // 3. 多级分组统计：单位 > 部门 > 人数
    // ============================================================

    private void seedStatistics() {
        Map<String, Object> config = baseConfig("人员统计报表");
        config.put("cellBindings", List.of(
                binding(0, 0, literal("人员统计"), "NONE", "LIST"),
                binding(1, 0, literal("单位"), "NONE", "LIST"),
                binding(1, 1, literal("部门"), "NONE", "LIST"),
                binding(1, 2, literal("人数"), "NONE", "LIST"),
                binding(1, 3, literal("总人数"), "NONE", "LIST"),
                binding(2, 0, fieldValue("staff", "unit"), "VERTICAL", "GROUP", true),
                binding(2, 1, fieldValue("staff", "dept"), "VERTICAL", "GROUP", true, cellKey(2, 0)),
                binding(2, 2, aggregate("COUNT", "staff", "name"), "VERTICAL", "LIST", false, cellKey(2, 1)),
                binding(2, 3, aggregate("COUNT", "staff", "name"), "VERTICAL", "LIST", true, cellKey(2, 0))
        ));
        config.put("template", buildWorkbook(List.of(
                cell(0, 0, "人员统计"),
                cell(1, 0, "单位"),
                cell(1, 1, "部门"),
                cell(1, 2, "人数"),
                cell(1, 3, "总人数")
        ), 6, 4));
        exampleIds.add(save(config));
    }

    // ============================================================
    // 4. 主从合并：员工 + 学历（1:N）
    // ============================================================

    private void seedMasterDetail() {
        Map<String, Object> config = baseConfig("员工学历信息表");
        config.put("cellBindings", List.of(
                binding(0, 0, literal("员工学历信息表"), "NONE", "LIST"),
                binding(1, 0, literal("姓名"), "NONE", "LIST"),
                binding(1, 1, literal("性别"), "NONE", "LIST"),
                binding(1, 2, literal("年龄"), "NONE", "LIST"),
                binding(1, 3, literal("学校"), "NONE", "LIST"),
                binding(1, 4, literal("专业"), "NONE", "LIST"),
                binding(1, 5, literal("毕业时间"), "NONE", "LIST"),
                binding(2, 0, fieldValue("emp_basic", "name"), "VERTICAL", "GROUP", true),
                binding(2, 1, fieldValue("emp_basic", "gender"), "VERTICAL", "GROUP", true, cellKey(2, 0)),
                binding(2, 2, fieldValue("emp_basic", "age"), "VERTICAL", "GROUP", true, cellKey(2, 1)),
                binding(2, 3, fieldValue("emp_education", "school"), "VERTICAL", "LIST"),
                binding(2, 4, fieldValue("emp_education", "major"), "VERTICAL", "LIST"),
                binding(2, 5, fieldValue("emp_education", "graduate_time"), "VERTICAL", "LIST")
        ));
        config.put("template", buildWorkbook(List.of(
                cell(0, 0, "员工学历信息表"),
                cell(1, 0, "姓名"),
                cell(1, 1, "性别"),
                cell(1, 2, "年龄"),
                cell(1, 3, "学校"),
                cell(1, 4, "专业"),
                cell(1, 5, "毕业时间")
        ), 8, 4));
        exampleIds.add(save(config));
    }

    // ============================================================
    // 5. 小计+总计：部门薪资分组小计 + 总计
    // ============================================================

    private void seedSubtotal() {
        Map<String, Object> config = baseConfig("薪资统计报表");
        config.put("cellBindings", List.of(
                binding(0, 0, literal("单位部门薪资统计表"), "NONE", "LIST"),
                binding(1, 0, literal("单位"), "NONE", "LIST"),
                binding(1, 1, literal("部门"), "NONE", "LIST"),
                binding(1, 2, literal("姓名"), "NONE", "LIST"),
                binding(1, 3, literal("薪资"), "NONE", "LIST"),
                binding(2, 0, fieldValue("salary_detail", "unit"), "VERTICAL", "GROUP", true),
                binding(2, 1, fieldValue("salary_detail", "dept"), "VERTICAL", "GROUP", true, cellKey(2, 0)),
                binding(2, 2, fieldValue("salary_detail", "name"), "VERTICAL", "LIST"),
                binding(2, 3, fieldValue("salary_detail", "salary"), "VERTICAL", "LIST")
        ));
        config.put("summaries", List.of(
                summary(Map.of("datasetId", "salary_detail", "field", "unit"), List.of(
                        labelCell(1, "${group}小计"),
                        aggCell(3, "salary_detail.salary", "SUM")
                )),
                summary(null, List.of(
                        labelCell(0, "总计"),
                        aggCell(3, "salary_detail.salary", "SUM")
                ))
        ));
        config.put("template", buildWorkbook(List.of(
                cell(0, 0, "单位部门薪资统计表"),
                cell(1, 0, "单位"),
                cell(1, 1, "部门"),
                cell(1, 2, "姓名"),
                cell(1, 3, "薪资")
        ), 8, 4));
        exampleIds.add(save(config));
    }

    // ============================================================
    // 6. 薪资条循环：员工循环 + 跨源查询
    // ============================================================

    private void seedPayslipLoop() {
        Map<String, Object> config = baseConfig("薪资条报表");

        // Template value for loop title
        Map<String, Object> templateValue = new LinkedHashMap<>();
        templateValue.put("type", "Template");
        templateValue.put("parts", List.of(
                Map.of("kind", "hole", "value", Map.of("type", "LoopFieldValue", "payload", "loop_emp.name")),
                Map.of("kind", "text", "text", "的薪资")
        ));

        // Conditions: salaries.emp_id == loop_emp.id
        Map<String, Object> loopCondition = Map.of(
                "id", "c1",
                "left", fieldValue("salaries", "emp_id"),
                "operator", "EQ",
                "right", Map.of("type", "LoopFieldValue", "payload", "loop_emp.id")
        );

        List<Map<String, Object>> bindings = new ArrayList<>();
        bindings.add(bindingWith(0, 0, templateValue, "NONE", "LIST"));
        bindings.add(binding(1, 0, literal("总薪资"), "NONE", "LIST"));
        bindings.add(binding(1, 1, literal("岗位薪资"), "NONE", "LIST"));
        bindings.add(binding(1, 2, literal("绩效工资"), "NONE", "LIST"));
        bindings.add(bindingWithConditions(2, 0, fieldValue("salaries", "total"), "NONE", "LIST", loopCondition));
        bindings.add(bindingWithConditions(2, 1, fieldValue("salaries", "base"), "NONE", "LIST", loopCondition));
        bindings.add(bindingWithConditions(2, 2, fieldValue("salaries", "bonus"), "NONE", "LIST", loopCondition));
        config.put("cellBindings", bindings);

        // Loop block
        config.put("loopBlocks", List.of(
                Map.of(
                        "id", "loop_emp",
                        "label", "员工循环",
                        "sheetId", SHEET,
                        "startRow", 0, "startColumn", 0,
                        "endRow", 3, "endColumn", 2,
                        "source", Map.of(
                                "datasetId", "employees",
                                "filters", List.of(Map.of(
                                        "id", "lf1",
                                        "left", fieldValue("employees", "status"),
                                        "operator", "EQ",
                                        "right", literal("在职")
                                )),
                                "groupBy", List.of(),
                                "orderBy", List.of()
                        )
                )
        ));

        config.put("template", buildWorkbook(List.of(
                cell(0, 0, "${loop_emp.name}的薪资"),
                cell(1, 0, "总薪资"),
                cell(1, 1, "岗位薪资"),
                cell(1, 2, "绩效工资")
        ), 6, 4));
        exampleIds.add(save(config));
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private String save(Map<String, Object> config) {
        return repository.save(config);
    }

    private Map<String, Object> baseConfig(String name) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("name", name);
        config.put("dataModelId", "default");
        config.put("_example", true);
        config.put("cellBindings", List.of());
        config.put("loopBlocks", List.of());
        config.put("summaries", List.of());
        config.put("params", List.of());
        return config;
    }

    private String cellKey(int row, int col) {
        return SHEET + ":" + row + ":" + col;
    }

    private Map<String, Object> literal(String text) {
        return Map.of("type", "Literal", "payload", text);
    }

    private Map<String, Object> fieldValue(String datasetId, String field) {
        return Map.of("type", "FieldValue", "payload", datasetId + "." + field);
    }

    private Map<String, Object> aggregate(String agg, String datasetId, String field) {
        return Map.of("type", "Aggregate", "aggregation", agg, "operand", fieldValue(datasetId, field));
    }

    private Map<String, Object> binding(int row, int col, Map<String, Object> value, String expansion, String expandMode) {
        return binding(row, col, value, expansion, expandMode, false, null);
    }

    private Map<String, Object> binding(int row, int col, Map<String, Object> value, String expansion, String expandMode, boolean mergeRepeated) {
        return binding(row, col, value, expansion, expandMode, mergeRepeated, null);
    }

    private Map<String, Object> binding(int row, int col, Map<String, Object> value, String expansion, String expandMode,
                                        boolean mergeRepeated, String parentCell) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("cellKey", cellKey(row, col));
        b.put("value", value);
        b.put("expansion", expansion);
        b.put("expandMode", expandMode);
        b.put("mergeRepeated", mergeRepeated);
        b.put("parentCell", parentCell);
        b.put("conditions", List.of());
        return b;
    }

    private Map<String, Object> bindingWith(int row, int col, Map<String, Object> value, String expansion, String expandMode) {
        return binding(row, col, value, expansion, expandMode, false, null);
    }

    private Map<String, Object> bindingWithConditions(int row, int col, Map<String, Object> value,
                                                      String expansion, String expandMode,
                                                      Map<String, Object> condition) {
        Map<String, Object> b = binding(row, col, value, expansion, expandMode, false, null);
        // Deep-copy condition with unique id
        Map<String, Object> c = new LinkedHashMap<>(condition);
        c.put("id", "c_" + row + "_" + col);
        b.put("conditions", List.of(c));
        return b;
    }

    private Map<String, Object> summary(Map<String, Object> groupBy, List<Map<String, Object>> cells) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", "sum-" + System.nanoTime());
        s.put("row", 0);
        s.put("groupBy", groupBy);
        s.put("cells", cells);
        return s;
    }

    private Map<String, Object> labelCell(int column, String payload) {
        return Map.of("column", column, "kind", "label", "payload", payload);
    }

    private Map<String, Object> aggCell(int column, String payload, String aggregation) {
        return Map.of("column", column, "kind", "agg", "payload", payload, "aggregation", aggregation);
    }

    // ─── ExcelWorkbook 构建 ───

    private record CellData(int row, int col, String value) {}

    private CellData cell(int row, int col, String value) {
        return new CellData(row, col, value);
    }

    private Map<String, Object> buildWorkbook(List<CellData> cells, int colCount, int rowCount) {
        List<Map<String, Object>> excelCells = new ArrayList<>();
        for (CellData c : cells) {
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("row", c.row);
            cell.put("col", c.col);
            cell.put("ref", toRef(c.row, c.col));
            cell.put("value", c.value);
            excelCells.add(cell);
        }

        Map<String, Object> sheet = new LinkedHashMap<>();
        sheet.put("id", SHEET);
        sheet.put("name", "Sheet1");
        sheet.put("rowCount", rowCount);
        sheet.put("columnCount", colCount);
        sheet.put("defaultRowHeight", 25);
        sheet.put("defaultColumnWidth", 100);
        sheet.put("merges", List.of());
        sheet.put("cells", excelCells);
        sheet.put("rows", List.of());
        sheet.put("columns", List.of());

        return Map.of("sheets", List.of(sheet));
    }

    private String toRef(int row, int col) {
        StringBuilder sb = new StringBuilder();
        int c = col;
        while (c >= 0) {
            sb.insert(0, (char) ('A' + c % 26));
            c = c / 26 - 1;
        }
        sb.append(row + 1);
        return sb.toString();
    }
}
