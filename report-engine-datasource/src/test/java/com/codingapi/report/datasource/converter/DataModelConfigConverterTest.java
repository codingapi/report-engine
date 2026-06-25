package com.codingapi.report.datasource.converter;

import static org.junit.jupiter.api.Assertions.*;

import com.codingapi.report.config.DataModelConfig;
import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.data.dataset.DataType;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.dataset.FieldRef;
import com.codingapi.report.data.dataset.TableDataset;
import com.codingapi.report.data.dataset.UnionDataset;
import com.codingapi.report.data.dataset.UnionMember;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.DataSourceType;
import com.codingapi.report.data.relation.JoinType;
import com.codingapi.report.data.relation.RelationOrigin;
import com.codingapi.report.data.relation.Relationship;
import com.codingapi.report.datasource.credential.CredentialService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataModelConfigConverterTest {

    private final DataModelConfigConverter converter =
            new DataModelConfigConverter(new CredentialService("test-key"));

    @Test
    void roundTripPreservesSemanticsAndEncryptsCredentials() {
        DataModel dm =
                DataModel.builder()
                        .id("m1")
                        .name("模型")
                        .datasources(
                                List.of(
                                        DataSource.builder()
                                                .id("ds")
                                                .name("db")
                                                .type(DataSourceType.DB)
                                                .config(
                                                        Map.of(
                                                                "url", "jdbc:h2:mem:x",
                                                                "password", "s3cret"))
                                                .build()))
                        .datasets(
                                List.of(
                                        TableDataset.builder()
                                                .id("emp")
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
                                                .build(),
                                        UnionDataset.builder()
                                                .id("all")
                                                .alias("全员")
                                                .fields(
                                                        List.of(
                                                                Field.builder()
                                                                        .name("name")
                                                                        .dataType(DataType.STRING)
                                                                        .build()))
                                                .members(
                                                        List.of(
                                                                new UnionMember(
                                                                        "emp",
                                                                        Map.of("name", "id"))))
                                                .build()))
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

        DataModelConfig cfg = converter.toConfig(dm);
        // 凭证已加密
        Object pwd = cfg.getDatasources().get(0).config().get("password");
        assertTrue(((String) pwd).startsWith("enc:"));
        assertEquals("jdbc:h2:mem:x", cfg.getDatasources().get(0).config().get("url"));
        // Dataset 两种形态用 kind 区分
        assertEquals("TABLE", cfg.getDatasets().get(0).kind());
        assertEquals("UNION", cfg.getDatasets().get(1).kind());
        assertEquals("LEFT", cfg.getRelationships().get(0).joinType());

        // 还原
        DataModel back = converter.toDataModel(cfg);
        assertEquals("m1", back.getId());
        assertEquals("s3cret", back.getDatasources().get(0).getConfig().get("password"));
        assertEquals(2, back.getDatasets().size());
        assertTrue(back.getDatasets().get(0) instanceof TableDataset);
        assertTrue(back.getDatasets().get(1) instanceof UnionDataset);
        assertEquals(JoinType.LEFT, back.getRelationships().get(0).getJoinType());
    }

    @Test
    void toDataModelHandlesNull() {
        assertNull(converter.toDataModel(null));
    }
}
