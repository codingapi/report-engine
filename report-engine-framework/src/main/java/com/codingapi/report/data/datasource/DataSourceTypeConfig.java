package com.codingapi.report.data.datasource;

import com.codingapi.report.data.datasource.type.DataSourceType;
import com.codingapi.report.data.datasource.type.DbDataSourceType;
import com.codingapi.report.dto.datasource.DataSourceTypeDTO;
import lombok.Builder;
import lombok.Data;

/**
 * 数据源类型配置（领域实体 + 仓库持久化载体）。
 *
 * <p>描述"这一类数据源本身需要什么"——当前人工创建的只有 {@link DbDataSourceType}（驱动 jar + 驱动类）； Excel/CSV 类型由系统参数 {@code
 * ReportProperties} 默认目录派生，不落用户记录。
 *
 * <p>面向前端时通过 {@link #toDTO()} 转成扁平 {@link DataSourceTypeDTO}，前端 JSON 通过 {@link
 * #fromDTO(DataSourceTypeDTO)} 还原。{@link #type} 复用现有 sealed {@link DataSourceType}，DTO 扁平化仅展开 DB
 * 的两个字段。
 */
@Data
@Builder
public class DataSourceTypeConfig {

    private String id;
    private String name;

    /** 类型级配置（sealed），人工创建仅 {@link DbDataSourceType}。 */
    private DataSourceType type;

    private long createTime;
    private long updateTime;

    public DataSourceTypeDTO toDTO() {
        String kind = type != null ? type.type() : null;
        String jarFile = null;
        String driverClass = null;
        if (type instanceof DbDataSourceType db) {
            jarFile = db.jarFile();
            driverClass = db.driverClass();
        }
        return new DataSourceTypeDTO(id, name, kind, jarFile, driverClass, createTime, updateTime);
    }

    public static DataSourceTypeConfig fromDTO(DataSourceTypeDTO dto) {
        if (dto == null) return null;
        return DataSourceTypeConfig.builder()
                .id(dto.id())
                .name(dto.name())
                .type(buildType(dto.kind(), dto.jarFile(), dto.driverClass()))
                .createTime(dto.createTime())
                .updateTime(dto.updateTime())
                .build();
    }

    private static DataSourceType buildType(String kind, String jarFile, String driverClass) {
        if (kind == null || kind.isBlank()) return null;
        return switch (kind) {
            case "DB" -> new DbDataSourceType(jarFile, driverClass);
            default -> throw new IllegalArgumentException("未知数据源类型: " + kind);
        };
    }
}
