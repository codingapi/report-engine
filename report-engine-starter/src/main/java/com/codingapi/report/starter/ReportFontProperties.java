package com.codingapi.report.starter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 字体配置属性。
 * <p>
 * 配置示例：
 * <pre>
 * report.fonts.dir=/path/to/custom/fonts
 * </pre>
 */
@ConfigurationProperties(prefix = "report.fonts")
@Data
public class ReportFontProperties {

    /**
     * 用户自定义字体目录路径（可选）。
     * <p>
     * 目录下的字体文件支持 .ttf / .otf / .ttc 格式。
     * 可通过文件名数字前缀控制排序（如 "01_微软雅黑.ttf"）。
     * 未配置时仅加载内置字体。
     * </p>
     */
    private String dir;
}
