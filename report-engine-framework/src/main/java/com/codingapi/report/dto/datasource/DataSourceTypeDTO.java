package com.codingapi.report.dto.datasource;

/**
 * 数据源类型配置持久化契约（扁平结构）。
 *
 * <p>当前人工创建仅 DB 类型，{@code jarFile}/{@code driverClass} 生效；{@code kind} 对齐 {@code
 * DataSourceType.type()} 判别串（"DB"）。Excel/CSV 类型由系统参数默认目录派生，不落用户记录，故 DTO 不携带 storagePath。
 */
public record DataSourceTypeDTO(
        String id,
        String name,
        String kind,
        String jarFile,
        String driverClass,
        long createTime,
        long updateTime) {}
