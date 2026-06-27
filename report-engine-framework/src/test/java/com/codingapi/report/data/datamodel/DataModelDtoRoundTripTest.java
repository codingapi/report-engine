package com.codingapi.report.data.datamodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingapi.report.dto.datamodel.DataModelDTO;
import com.codingapi.report.data.dataset.DataType;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.dataset.UnionDataset;
import com.codingapi.report.data.dataset.UnionMember;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.type.DbDataSourceType;
import com.codingapi.report.data.relation.JoinType;
import com.codingapi.report.data.relation.RelationOrigin;
import com.codingapi.report.data.relation.Relationship;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code DataModel.toDTO()} ↔ {@code DataModel.fromDTO()} 往返保真，连接由 TableDataset 自带、去重收集。 */
class DataModelDtoRoundTripTest {

    @Test
    void roundTripPreservesSemantics() {
        DataSource source =
                DataSource.builder()
                        .id("ds")
                        .name("db")
                        .type(new DbDataSourceType(null, null))
                        .config(Map.of("url", "jdbc:h2:mem:x", "password", "s3cret"))
                        .build();
        TableDataset emp =
                TableDataset.builder()
                        .id("emp")
                        .datasource(source)
                        .datasourceId("ds")
                        .sourceTable("EMP")
                        .alias("员工")
                        .fields(
                                List.of(
                                        Field.builder()
                                                .name("id")
                                                .dataType(DataType.NUMBER)
                                                .primaryKey(true)
                                                .build()))
                        .build();
        UnionDataset all =
                UnionDataset.builder()
                        .id("all")
                        .alias("全员")
                        .fields(List.of(Field.builder().name("name").dataType(DataType.STRING).build()))
                        .members(List.of(new UnionMember("emp", Map.of("name", "id"))))
                        .build();
        DataModel dm =
                DataModel.builder()
                        .id("m1")
                        .name("模型")
                        .status(DataModelStatus.PUBLISHED)
                        .datasets(List.of(emp, all))
                        .relationships(
                                List.of(
                                        Relationship.builder()
                                                .id("r1")
                                                .left(new FieldRef("emp", "id"))
                                                .right(new FieldRef("all", "name"))
                                                .joinType(JoinType.LEFT)
                                                .origin(RelationOrigin.MANUAL)
                                                .build()))
                        .build();

        DataModelDTO dto = dm.toDTO();
        // 连接由 TableDataset 去重收集
        assertEquals(1, dto.datasources().size());
        assertEquals("DB", dto.datasources().get(0).type());
        // 明文配置（落盘加密交使用方仓库）
        assertEquals("s3cret", dto.datasources().get(0).config().get("password"));
        assertEquals("PUBLISHED", dto.status());
        assertEquals("TABLE", dto.datasets().get(0).kind());
        assertEquals("UNION", dto.datasets().get(1).kind());
        assertEquals("LEFT", dto.relationships().get(0).joinType());

        // 还原
        DataModel back = DataModel.fromDTO(dto);
        assertEquals("m1", back.getId());
        assertEquals(DataModelStatus.PUBLISHED, back.getStatus());
        assertEquals(2, back.getDatasets().size());
        assertTrue(back.getDatasets().get(0) instanceof TableDataset);
        assertTrue(back.getDatasets().get(1) instanceof UnionDataset);
        TableDataset backEmp = (TableDataset) back.getDatasets().get(0);
        assertEquals("s3cret", backEmp.getDatasource().getConfig().get("password"));
        assertEquals(JoinType.LEFT, back.getRelationships().get(0).getJoinType());
    }

    @Test
    void fromDtoNull() {
        assertNull(DataModel.fromDTO(null));
    }
}
