package com.codingapi.report.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.codingapi.report.dto.report.BindingDTO;
import com.codingapi.report.dto.report.ConditionDTO;
import com.codingapi.report.dto.report.PartDTO;
import com.codingapi.report.dto.report.SummaryCellDTO;
import com.codingapi.report.dto.report.SummaryRowDTO;
import com.codingapi.report.dto.report.ValueDTO;
import com.codingapi.report.dto.report.ReportDTO;
import com.codingapi.report.dto.report.ParamDTO;
import com.codingapi.report.core.grid.CellBinding;
import com.codingapi.report.expression.Value;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code ReportDTO} → {@code Report.fromDTO} → {@code Report.toDTO} 往返保真。
 *
 * <p>验证 sealed {@code Value} 树（含 Template/Aggregate/FunctionCall 嵌套）、单元格控制层、条件、汇总、参数 不在领域往返中丢失语义。前端展示字段（preview/param.id/summary.id）按设计不持久化。
 */
class ReportDtoRoundTripTest {

    @Test
    void roundTripPreservesSemantics() {
        // 值表达式：Template([Text, Hole(Aggregate(SUM, FieldValue d.amount))])
        ValueDTO agg =
                new ValueDTO(
                        "Aggregate",
                        null,
                        "SUM",
                        new ValueDTO("FieldValue", "d.amount", null, null, null, null, null),
                        null,
                        null,
                        null);
        ValueDTO template =
                new ValueDTO(
                        "Template",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(
                                new PartDTO("text", "合计 ", null),
                                new PartDTO("hole", null, agg)));

        BindingDTO binding =
                new BindingDTO(
                        "sheet1:2:1",
                        template,
                        "VERTICAL",
                        "LIST",
                        true,
                        "sheet1:1:0",
                        List.of(
                                new ConditionDTO(
                                        null,
                                        new ValueDTO(
                                                "FieldValue", "d.dept", null, null, null, null,
                                                null),
                                        "EQ",
                                        new ValueDTO(
                                                "ParamValue", "deptId", null, null, null, null,
                                                null))),
                        false,
                        "设计期预览文本",
                        true,
                        "d");

        SummaryRowDTO summary =
                new SummaryRowDTO(
                        "sumRow-1",
                        "VERTICAL",
                        null,
                        0,
                        1,
                        List.of(
                                new SummaryCellDTO(
                                        1,
                                        new ValueDTO(
                                                "Aggregate",
                                                null,
                                                "SUM",
                                                new ValueDTO(
                                                        "FieldValue", "d.amount", null, null, null,
                                                        null, null),
                                                null,
                                                null,
                                                null),
                                        null,
                                        null,
                                        null,
                                        null,
                                        true,
                                        "d")),
                        0);

        ParamDTO param = new ParamDTO();
        param.setId("p1");
        param.setName("deptId");
        param.setAlias("部门");
        param.setDataType("NUMBER");
        param.setDefaultValue("5");

        ReportDTO dto = new ReportDTO();
        dto.setName("测试报表");
        dto.setDataModelId("default");
        dto.setCellBindings(List.of(binding));
        dto.setSummaries(List.of(summary));
        dto.setParams(List.of(param));

        // 往返
        ReportDTO back = Report.fromDTO(dto).toDTO();

        assertEquals("测试报表", back.getName());
        assertEquals("default", back.getDataModelId());

        // 单元格控制层 + 值树
        BindingDTO b = back.getCellBindings().get(0);
        assertEquals("sheet1:2:1", b.cellKey());
        assertEquals("VERTICAL", b.expansion());
        assertEquals("LIST", b.expandMode());
        assertEquals(true, b.mergeRepeated());
        assertEquals("sheet1:1:0", b.parentCell());
        assertEquals(true, b.drillEnabled());
        assertEquals("d", b.drillView());
        assertNull(b.preview(), "preview 前端字段不持久化");

        // 值树：Template → Hole(Aggregate(FieldValue))
        assertEquals("Template", b.value().type());
        assertEquals("合计 ", b.value().parts().get(0).text());
        ValueDTO hole = b.value().parts().get(1).value();
        assertEquals("Aggregate", hole.type());
        assertEquals("SUM", hole.aggregation());
        assertEquals("d.amount", hole.operand().payload());

        // 条件
        ConditionDTO c = b.conditions().get(0);
        assertEquals("d.dept", c.left().payload());
        assertEquals("EQ", c.operator());
        assertEquals("deptId", c.right().payload());

        // 汇总
        SummaryRowDTO s = back.getSummaries().get(0);
        assertEquals("VERTICAL", s.axis());
        assertEquals("SUM", s.cells().get(0).value().aggregation());
        assertEquals("d", s.cells().get(0).drillView());

        // 参数（语义保真；id 前端字段不持久化）
        ParamDTO rp = back.getParams().get(0);
        assertEquals("deptId", rp.getName());
        assertEquals("部门", rp.getAlias());
        assertEquals("NUMBER", rp.getDataType());
        assertEquals("5", rp.getDefaultValue());
        assertNull(rp.getId(), "param.id 前端字段不持久化");
    }

    @Test
    void literalAndFieldValueRoundTrip() {
        Value lit = RenderDtoConverter.convertValue(new ValueDTO("Literal", "hello", null, null, null, null, null));
        assertInstanceOf(Value.Literal.class, lit);
        assertEquals("hello", RenderDtoConverter.toValueDto(lit).payload());

        CellBinding fieldCell =
                RenderDtoConverter.convertBindings(
                                List.of(
                                        new BindingDTO(
                                                "sheet1:0:0",
                                                new ValueDTO(
                                                        "FieldValue", "ds.name", null, null, null,
                                                        null, null),
                                                "NONE",
                                                null,
                                                false,
                                                null,
                                                List.of(),
                                                false,
                                                null,
                                                false,
                                                null)))
                        .get(0);
        assertEquals("ds.name", RenderDtoConverter.toValueDto(fieldCell.getValue()).payload());
    }
}
