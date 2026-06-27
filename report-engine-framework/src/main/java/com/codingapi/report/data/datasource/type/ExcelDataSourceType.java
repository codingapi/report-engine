package com.codingapi.report.data.datasource.type;

/**
 * Excel 数据源类型。
 *
 * <p>{@code storagePath} 为上传 Excel 文件的存储路径；具体文件名/sheet 由 {@code DataSource.config} 与 {@code
 * TableDataset.sourceTable} 描述。
 */
public record ExcelDataSourceType(String storagePath) implements DataSourceType {

    @Override
    public String type() {
        return "EXCEL";
    }
}
