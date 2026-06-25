package com.codingapi.report.core.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.data.datasource.RawTable;
import com.codingapi.report.data.relation.JoinType;
import com.codingapi.report.data.relation.RelationOrigin;
import com.codingapi.report.data.relation.Relationship;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link Operators#join} 的外连接测试：覆盖 INNER/LEFT/RIGHT/FULL + 一对多 + null 填充。
 *
 * <p>数据：员工表 d_emp（1/2/3），学历表 d_edu（emp_id=1×2 一对多、emp_id=2、emp_id=9 无匹配员工）。
 */
class OperatorsTest {

    private static RawTable empTable() {
        return new RawTable(
                List.of("d_emp.id", "d_emp.name"),
                List.of(
                        row("d_emp.id", 1, "d_emp.name", "张三"),
                        row("d_emp.id", 2, "d_emp.name", "李四"),
                        row("d_emp.id", 3, "d_emp.name", "王五")));
    }

    private static RawTable eduTable() {
        return new RawTable(
                List.of("d_edu.emp_id", "d_edu.degree"),
                List.of(
                        row("d_edu.emp_id", 1, "d_edu.degree", "本科"),
                        row("d_edu.emp_id", 1, "d_edu.degree", "硕士"),
                        row("d_edu.emp_id", 2, "d_edu.degree", "专科"),
                        row("d_edu.emp_id", 9, "d_edu.degree", "博士") // 无匹配员工
                        ));
    }

    /** 关系端点：d_edu.emp_id ↔ d_emp.id（端点方向与 join 参数 left/right 无关，方向由参数位置定） */
    private static Relationship rel(JoinType jt) {
        return Relationship.builder()
                .id("rel")
                .left(new FieldRef("d_edu", "emp_id"))
                .right(new FieldRef("d_emp", "id"))
                .joinType(jt)
                .origin(RelationOrigin.MANUAL)
                .build();
    }

    @Test
    void inner_shouldKeepOnlyMatched() {
        RawTable out = Operators.join(empTable(), eduTable(), rel(JoinType.INNER));
        assertEquals(3, out.getRows().size(), "INNER：emp1×2 + emp2×1，emp3 与 emp9 丢弃");
        // 一对多：emp1 产出两行
        assertEquals(
                2, out.getRows().stream().filter(r -> "张三".equals(r.get("d_emp.name"))).count());
    }

    @Test
    void left_shouldKeepAllLeftWithNullFill() {
        RawTable out = Operators.join(empTable(), eduTable(), rel(JoinType.LEFT));
        assertEquals(4, out.getRows().size(), "LEFT：emp1×2 + emp2×1 + emp3 补 null");
        // emp3 无匹配：保留行，学历列补 null
        Map<String, Object> emp3Row =
                out.getRows().stream()
                        .filter(r -> "王五".equals(r.get("d_emp.name")))
                        .findFirst()
                        .orElseThrow();
        assertNull(emp3Row.get("d_edu.degree"), "emp3 无学历，degree 应为 null");
        assertNull(emp3Row.get("d_edu.emp_id"), "emp3 无学历，emp_id 应为 null");
        // 列含双方
        assertTrue(
                out.getColumns()
                        .containsAll(
                                List.of("d_emp.id", "d_emp.name", "d_edu.emp_id", "d_edu.degree")));
    }

    @Test
    void right_shouldKeepAllRightWithNullFill() {
        RawTable out = Operators.join(empTable(), eduTable(), rel(JoinType.RIGHT));
        assertEquals(4, out.getRows().size(), "RIGHT：d_edu 全部 4 行（emp9 无匹配员工补 null）");
        // emp9 博士未匹配员工：员工列补 null
        Map<String, Object> edu9Row =
                out.getRows().stream()
                        .filter(r -> "博士".equals(r.get("d_edu.degree")))
                        .findFirst()
                        .orElseThrow();
        assertNull(edu9Row.get("d_emp.id"), "emp9 无员工，id 应为 null");
        assertNull(edu9Row.get("d_emp.name"), "emp9 无员工，name 应为 null");
    }

    @Test
    void full_shouldKeepBothSides() {
        RawTable out = Operators.join(empTable(), eduTable(), rel(JoinType.FULL));
        assertEquals(5, out.getRows().size(), "FULL：emp1×2 + emp2×1 + emp3 补 null + emp9 补 null");
        // 两侧无匹配行都存在
        assertTrue(
                out.getRows().stream()
                        .anyMatch(
                                r ->
                                        "王五".equals(r.get("d_emp.name"))
                                                && r.get("d_edu.degree") == null),
                "emp3 保留且学历为 null");
        assertTrue(
                out.getRows().stream()
                        .anyMatch(
                                r ->
                                        "博士".equals(r.get("d_edu.degree"))
                                                && r.get("d_emp.name") == null),
                "emp9 保留且员工为 null");
    }

    @Test
    void nullJoinType_shouldDefaultToInner() {
        Relationship noType =
                Relationship.builder()
                        .id("rel")
                        .left(new FieldRef("d_edu", "emp_id"))
                        .right(new FieldRef("d_emp", "id"))
                        .origin(RelationOrigin.MANUAL)
                        .build();
        RawTable out = Operators.join(empTable(), eduTable(), noType);
        assertEquals(3, out.getRows().size(), "JoinType 为 null 时按 INNER 处理");
    }

    /** 构造一行（LinkedHashMap 保留列序，允许 null 值，Map.of 不允许 null） */
    @SafeVarargs
    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
