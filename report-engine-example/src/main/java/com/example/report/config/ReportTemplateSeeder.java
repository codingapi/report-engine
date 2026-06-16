package com.example.report.config;

import com.codingapi.report.starter.repository.ExampleReportRegistry;
import com.codingapi.report.starter.repository.ReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.example.report.config.ReportConfigBuilder.aggCell;
import static com.example.report.config.ReportConfigBuilder.aggregate;
import static com.example.report.config.ReportConfigBuilder.cell;
import static com.example.report.config.ReportConfigBuilder.cellKey;
import static com.example.report.config.ReportConfigBuilder.fieldValue;
import static com.example.report.config.ReportConfigBuilder.labelCell;
import static com.example.report.config.ReportConfigBuilder.literal;

/**
 * 预存示例报表：启动时将测试报表配置写入仓库（应用级数据）。
 * <p>
 * 对应 ReportScenarioTest 中的 8 个场景。前端通过
 * {@code GET /api/report/configs/examples} 获取列表，
 * 点击即导航到 {@code /engine?id=xxx} 打开对应报表。
 * <p>
 * 实现 {@link ExampleReportRegistry}，向 starter 的 ReportConfigController 提供有序示例 id。
 * 配置用 {@link ReportConfigBuilder} 链式构造。
 * </p>
 */
@Slf4j
@Component
public class ReportTemplateSeeder implements ExampleReportRegistry {

    private static final String SHEET = "sheet1";

    private final ReportRepository repository;

    /** 预存报表的 id 集合，供 API 过滤用。 */
    private final List<String> exampleIds = new ArrayList<>();

    public ReportTemplateSeeder(ReportRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<String> exampleReportIds() {
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
        seedIndependentBands();
        seedIndependentBandsWithSummary();
        log.info("已预存 {} 个示例报表", exampleIds.size());
    }

    // ============================================================
    // 1. 简单列表：商品名 + 价格 + 合计
    // ============================================================

    private void seedSimpleList() {
        save(new ReportConfigBuilder("商品清单报表")
                .binding(0, 0, literal("商品清单"), "NONE", "LIST")
                .binding(1, 0, literal("商品名"), "NONE", "LIST")
                .binding(1, 1, literal("价格"), "NONE", "LIST")
                .binding(2, 0, fieldValue("products", "name"), "VERTICAL", "LIST")
                .binding(2, 1, fieldValue("products", "price"), "VERTICAL", "LIST")
                .summary(3, 0, 1, null, List.of(
                        labelCell(0, "合计"),
                        aggCell(1, "products.price", "SUM")))
                .template(6, 5,
                        cell(0, 0, "商品清单"),
                        cell(1, 0, "商品名"),
                        cell(1, 1, "价格"),
                        cell(3, 0, "合计"))
                .build());
    }

    // ============================================================
    // 2. 分组列表：销售分类合并 + 商品明细
    // ============================================================

    private void seedMergedList() {
        save(new ReportConfigBuilder("销售明细报表")
                .binding(0, 0, literal("销售明细"), "NONE", "LIST")
                .binding(1, 0, literal("分类"), "NONE", "LIST")
                .binding(1, 1, literal("商品"), "NONE", "LIST")
                .binding(1, 2, literal("数量"), "NONE", "LIST")
                .binding(2, 0, fieldValue("sales", "category"), "VERTICAL", "GROUP", true)
                .binding(2, 1, fieldValue("sales", "product"), "VERTICAL", "LIST")
                .binding(2, 2, fieldValue("sales", "qty"), "VERTICAL", "LIST")
                .template(6, 4,
                        cell(0, 0, "销售明细"),
                        cell(1, 0, "分类"),
                        cell(1, 1, "商品"),
                        cell(1, 2, "数量"))
                .build());
    }

    // ============================================================
    // 3. 多级分组统计：单位 > 部门 > 人数
    // ============================================================

    private void seedStatistics() {
        save(new ReportConfigBuilder("人员统计报表")
                .binding(0, 0, literal("人员统计"), "NONE", "LIST")
                .binding(1, 0, literal("单位"), "NONE", "LIST")
                .binding(1, 1, literal("部门"), "NONE", "LIST")
                .binding(1, 2, literal("人数"), "NONE", "LIST")
                .binding(1, 3, literal("总人数"), "NONE", "LIST")
                .binding(2, 0, fieldValue("staff", "unit"), "VERTICAL", "GROUP", true)
                .binding(2, 1, fieldValue("staff", "dept"), "VERTICAL", "GROUP", true, cellKey(2, 0))
                .binding(2, 2, aggregate("COUNT", "staff", "name"), "VERTICAL", "LIST", false, cellKey(2, 1))
                .binding(2, 3, aggregate("COUNT", "staff", "name"), "VERTICAL", "LIST", true, cellKey(2, 0))
                .template(6, 4,
                        cell(0, 0, "人员统计"),
                        cell(1, 0, "单位"),
                        cell(1, 1, "部门"),
                        cell(1, 2, "人数"),
                        cell(1, 3, "总人数"))
                .build());
    }

    // ============================================================
    // 4. 主从合并：员工 + 学历（1:N）
    // ============================================================

    private void seedMasterDetail() {
        save(new ReportConfigBuilder("员工学历信息表")
                .binding(0, 0, literal("员工学历信息表"), "NONE", "LIST")
                .binding(1, 0, literal("姓名"), "NONE", "LIST")
                .binding(1, 1, literal("性别"), "NONE", "LIST")
                .binding(1, 2, literal("年龄"), "NONE", "LIST")
                .binding(1, 3, literal("学校"), "NONE", "LIST")
                .binding(1, 4, literal("专业"), "NONE", "LIST")
                .binding(1, 5, literal("毕业时间"), "NONE", "LIST")
                .binding(2, 0, fieldValue("emp_basic", "name"), "VERTICAL", "GROUP", true)
                .binding(2, 1, fieldValue("emp_basic", "gender"), "VERTICAL", "GROUP", true, cellKey(2, 0))
                .binding(2, 2, fieldValue("emp_basic", "age"), "VERTICAL", "GROUP", true, cellKey(2, 1))
                .binding(2, 3, fieldValue("emp_education", "school"), "VERTICAL", "LIST")
                .binding(2, 4, fieldValue("emp_education", "major"), "VERTICAL", "LIST")
                .binding(2, 5, fieldValue("emp_education", "graduate_time"), "VERTICAL", "LIST")
                .template(8, 4,
                        cell(0, 0, "员工学历信息表"),
                        cell(1, 0, "姓名"),
                        cell(1, 1, "性别"),
                        cell(1, 2, "年龄"),
                        cell(1, 3, "学校"),
                        cell(1, 4, "专业"),
                        cell(1, 5, "毕业时间"))
                .build());
    }

    // ============================================================
    // 5. 小计+总计：部门薪资分组小计 + 总计
    // ============================================================

    private void seedSubtotal() {
        save(new ReportConfigBuilder("薪资统计报表")
                .binding(0, 0, literal("单位部门薪资统计表"), "NONE", "LIST")
                .binding(1, 0, literal("单位"), "NONE", "LIST")
                .binding(1, 1, literal("部门"), "NONE", "LIST")
                .binding(1, 2, literal("姓名"), "NONE", "LIST")
                .binding(1, 3, literal("薪资"), "NONE", "LIST")
                .binding(2, 0, fieldValue("salary_detail", "unit"), "VERTICAL", "GROUP", true)
                .binding(2, 1, fieldValue("salary_detail", "dept"), "VERTICAL", "GROUP", true, cellKey(2, 0))
                .binding(2, 2, fieldValue("salary_detail", "name"), "VERTICAL", "LIST")
                .binding(2, 3, fieldValue("salary_detail", "salary"), "VERTICAL", "LIST")
                .summary(3, 0, 3, Map.of("datasetId", "salary_detail", "field", "unit"), List.of(
                        labelCell(1, "${group}小计"),
                        aggCell(3, "salary_detail.salary", "SUM")))
                .summary(4, 0, 3, null, List.of(
                        labelCell(0, "总计"),
                        aggCell(3, "salary_detail.salary", "SUM")))
                .template(8, 6,
                        cell(0, 0, "单位部门薪资统计表"),
                        cell(1, 0, "单位"),
                        cell(1, 1, "部门"),
                        cell(1, 2, "姓名"),
                        cell(1, 3, "薪资"),
                        cell(3, 1, "${group}小计"),
                        cell(4, 0, "总计"))
                .build());
    }

    // ============================================================
    // 6. 薪资条循环：员工循环 + 跨源查询
    // ============================================================

    private void seedPayslipLoop() {
        // 循环标题：${loop_emp.name}的薪资（LoopFieldValue + 文本，示例特有结构）
        Map<String, Object> templateValue = new LinkedHashMap<>();
        templateValue.put("type", "Template");
        templateValue.put("parts", List.of(
                Map.of("kind", "hole", "value", Map.of("type", "LoopFieldValue", "payload", "loop_emp.name")),
                Map.of("kind", "text", "text", "的薪资")
        ));

        // 条件：salaries.emp_id == loop_emp.id
        Map<String, Object> loopCondition = Map.of(
                "id", "c1",
                "left", fieldValue("salaries", "emp_id"),
                "operator", "EQ",
                "right", Map.of("type", "LoopFieldValue", "payload", "loop_emp.id")
        );

        Map<String, Object> loopBlock = Map.of(
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
        );

        save(new ReportConfigBuilder("薪资条报表")
                .binding(0, 0, templateValue, "NONE", "LIST")
                .binding(1, 0, literal("总薪资"), "NONE", "LIST")
                .binding(1, 1, literal("岗位薪资"), "NONE", "LIST")
                .binding(1, 2, literal("绩效工资"), "NONE", "LIST")
                .bindingWithConditions(2, 0, fieldValue("salaries", "total"), "NONE", "LIST", loopCondition)
                .bindingWithConditions(2, 1, fieldValue("salaries", "base"), "NONE", "LIST", loopCondition)
                .bindingWithConditions(2, 2, fieldValue("salaries", "bonus"), "NONE", "LIST", loopCondition)
                .loopBlock(loopBlock)
                .template(6, 4,
                        cell(0, 0, "${loop_emp.name}的薪资"),
                        cell(1, 0, "总薪资"),
                        cell(1, 1, "岗位薪资"),
                        cell(1, 2, "绩效工资"))
                .build());
    }

    // ============================================================
    // 7. 独立数据带：员工 + 商品并列（无关系，各自独立展开）
    // ============================================================

    private void seedIndependentBands() {
        save(new ReportConfigBuilder("员工商品并列报表")
                .binding(0, 0, literal("姓名"), "NONE", "LIST")
                .binding(0, 1, literal("单位"), "NONE", "LIST")
                .binding(0, 2, literal("商品名"), "NONE", "LIST")
                .binding(0, 3, literal("价格"), "NONE", "LIST")
                // 员工数据带（col 0-1, VERTICAL）
                .binding(1, 0, fieldValue("staff", "name"), "VERTICAL", "LIST")
                .binding(1, 1, fieldValue("staff", "unit"), "VERTICAL", "LIST")
                // 商品数据带（col 2-3, VERTICAL）— 与员工无关系，各自独立展开
                .binding(1, 2, fieldValue("products", "name"), "VERTICAL", "LIST")
                .binding(1, 3, fieldValue("products", "price"), "VERTICAL", "LIST")
                // 员工总数（single 聚合，会被 shift 下移）
                .binding(2, 0, literal("员工总数"), "NONE", "LIST")
                .binding(2, 1, aggregate("COUNT", "staff", "name"), "NONE", "LIST")
                .template(8, 6,
                        cell(0, 0, "姓名"),
                        cell(0, 1, "单位"),
                        cell(0, 2, "商品名"),
                        cell(0, 3, "价格"),
                        cell(2, 0, "员工总数"))
                .build());
    }

    // ============================================================
    // 8. 独立数据带 + 各带行汇总：员工合计(col 0-1) / 商品合计(col 2-3) 互不串扰
    // ============================================================

    private void seedIndependentBandsWithSummary() {
        // 各带各自一行总计：列区间决定作用域（后端 summariesForBand 按 [fromColumn,toColumn] 与
        // 数据带列集合求交归属），互不串扰。两个汇总同锚在设计行 2，分别占列区间 [0,1] 和 [2,3]——
        // 前端右键框选区域即生成该区间，同一设计行可并列多个独立汇总。渲染时后端按各带真实行数
        // 分别落位，与设计态行号无关（员工带 4 行、商品带 3 行 → 各自追加在自己数据末尾）。
        save(new ReportConfigBuilder("员工商品并列汇总报表")
                .binding(0, 0, literal("姓名"), "NONE", "LIST")
                .binding(0, 1, literal("单位"), "NONE", "LIST")
                .binding(0, 2, literal("商品名"), "NONE", "LIST")
                .binding(0, 3, literal("价格"), "NONE", "LIST")
                // 员工数据带（col 0-1, VERTICAL）
                .binding(1, 0, fieldValue("staff", "name"), "VERTICAL", "LIST")
                .binding(1, 1, fieldValue("staff", "unit"), "VERTICAL", "LIST")
                // 商品数据带（col 2-3, VERTICAL）
                .binding(1, 2, fieldValue("products", "name"), "VERTICAL", "LIST")
                .binding(1, 3, fieldValue("products", "price"), "VERTICAL", "LIST")
                .summary(2, 0, 1, null, List.of(
                        labelCell(0, "员工合计"),
                        aggCell(1, "staff.name", "COUNT")))
                .summary(2, 2, 3, null, List.of(
                        labelCell(2, "商品合计"),
                        aggCell(3, "products.price", "SUM")))
                .template(8, 6,
                        cell(0, 0, "姓名"),
                        cell(0, 1, "单位"),
                        cell(0, 2, "商品名"),
                        cell(0, 3, "价格"),
                        cell(2, 0, "员工合计"),
                        cell(2, 2, "商品合计"))
                .build());
    }

    // ============================================================
    // 辅助
    // ============================================================

    /** 保存配置并登记示例 id。 */
    private void save(Map<String, Object> config) {
        exampleIds.add(repository.save(config));
    }
}
