package com.codingapi.report.starter.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Report Engine 全局配置属性。
 *
 * <p>配置示例：
 *
 * <pre>
 * codingapi.report.font.dir=/path/to/custom/fonts
 * codingapi.report.driver.dir=/path/to/drivers
 * codingapi.report.excel.dir=/path/to/excel
 * codingapi.report.csv.dir=/path/to/csv
 * </pre>
 */
@ConfigurationProperties(prefix = "codingapi.report")
@Data
public class ReportProperties {

    /** 字体配置。 */
    private FontProperty font = new FontProperty();

    /** 驱动配置。 */
    private DriverProperty driver = new DriverProperty();

    /** Excel 上传存储配置。 */
    private ExcelProperty excel = new ExcelProperty();

    /** CSV 上传存储配置。 */
    private CsvProperty csv = new CsvProperty();

    /** 字体目录配置。 */
    @Data
    public static class FontProperty {
        /** 用户自定义字体目录路径（可选）；为 null/空时仅加载内置字体。 */
        private String dir;
    }

    /** 驱动 jar 存储目录配置。 */
    @Data
    public static class DriverProperty {
        /** 驱动 jar 存储目录，默认 {@code ./data/drivers}。 */
        private String dir = "./data/drivers";
    }

    /** Excel 上传存储目录配置。 */
    @Data
    public static class ExcelProperty {
        /** Excel 上传存储目录，默认 {@code ./data/excel}。 */
        private String dir = "./data/excel";
    }

    /** CSV 上传存储目录配置。 */
    @Data
    public static class CsvProperty {
        /** CSV 上传存储目录，默认 {@code ./data/csv}。 */
        private String dir = "./data/csv";
    }
}
