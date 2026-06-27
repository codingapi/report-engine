package com.codingapi.report.data.datasource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingapi.report.data.datasource.type.DbDataSourceType;
import com.codingapi.report.dto.datasource.DataSourceTypeDTO;
import org.junit.jupiter.api.Test;

/**
 * {@link DataSourceTypeConfig#toDTO()} / {@link DataSourceTypeConfig#fromDTO(DataSourceTypeDTO)}
 * 往返保真。
 */
class DataSourceTypeConfigTest {

    @Test
    void roundTripPreservesDbConfig() {
        DataSourceTypeConfig config =
                DataSourceTypeConfig.builder()
                        .id("dst-1")
                        .name("MySQL 8 驱动")
                        .type(
                                new DbDataSourceType(
                                        "/data/drivers/mysql.jar", "com.mysql.cj.jdbc.Driver"))
                        .createTime(1700_000_000_000L)
                        .updateTime(1700_000_000_000L)
                        .build();

        DataSourceTypeDTO dto = config.toDTO();
        assertEquals("dst-1", dto.id());
        assertEquals("MySQL 8 驱动", dto.name());
        assertEquals("DB", dto.kind());
        assertEquals("/data/drivers/mysql.jar", dto.jarFile());
        assertEquals("com.mysql.cj.jdbc.Driver", dto.driverClass());
        assertEquals(1700_000_000_000L, dto.createTime());
        assertEquals(1700_000_000_000L, dto.updateTime());

        DataSourceTypeConfig back = DataSourceTypeConfig.fromDTO(dto);
        assertEquals(config.getId(), back.getId());
        assertEquals(config.getName(), back.getName());
        assertTrue(back.getType() instanceof DbDataSourceType);
        DbDataSourceType db = (DbDataSourceType) back.getType();
        assertEquals("DB", db.type());
        assertEquals("/data/drivers/mysql.jar", db.jarFile());
        assertEquals("com.mysql.cj.jdbc.Driver", db.driverClass());
        assertEquals(config.getCreateTime(), back.getCreateTime());
        assertEquals(config.getUpdateTime(), back.getUpdateTime());
    }

    @Test
    void fromDTORejectsUnknownKind() {
        DataSourceTypeDTO dto = new DataSourceTypeDTO("dst-x", "未知", "REDIS", null, null, 0L, 0L);
        assertThrows(IllegalArgumentException.class, () -> DataSourceTypeConfig.fromDTO(dto));
    }

    @Test
    void fromDTOBlankKindYieldsNullType() {
        DataSourceTypeDTO dto = new DataSourceTypeDTO("dst-y", "空类型", null, null, null, 0L, 0L);
        DataSourceTypeConfig config = DataSourceTypeConfig.fromDTO(dto);
        assertNotNull(config);
        assertNull(config.getType());
    }

    @Test
    void fromNullDtoReturnsNull() {
        assertNull(DataSourceTypeConfig.fromDTO(null));
    }
}
