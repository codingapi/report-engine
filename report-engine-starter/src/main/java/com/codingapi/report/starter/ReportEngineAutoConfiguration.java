package com.codingapi.report.starter;

import com.codingapi.report.excel.FontRegistry;
import com.codingapi.report.starter.controller.ExcelController;
import com.codingapi.report.starter.controller.FontController;
import com.codingapi.report.starter.properties.ReportFontProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Report Engine 自动配置。
 * <p>
 * 自动装配 {@link FontRegistry} Bean：
 * 提取内置字体 → 合并用户自定义字体 → 扫描 → 注册到 JVM
 * </p>
 */
@Configuration
@EnableConfigurationProperties(ReportFontProperties.class)
public class ReportEngineAutoConfiguration {

    @Bean
    public FontRegistry fontRegistry(ReportFontProperties properties) throws IOException {
        Path builtinDir = FontRegistry.extractBuiltinFonts();

        Path customDir = null;
        if (properties.getDir() != null && !properties.getDir().isBlank()) {
            customDir = Path.of(properties.getDir());
        }

        FontRegistry registry = (customDir != null)
                ? new FontRegistry(builtinDir, customDir)
                : new FontRegistry(builtinDir);

        registry.scanDirectory();
        registry.registerToGraphicsEnvironment();
        return registry;
    }

    /**
     * Web 环境下的自动配置：注册 REST API Controller。
     */
    @Configuration
    @ConditionalOnClass(RestController.class)
    static class WebConfiguration {

        @Bean
        public FontController fontController(FontRegistry fontRegistry) {
            return new FontController(fontRegistry);
        }

        @Bean
        public ExcelController excelController() {
            return new ExcelController();
        }
    }
}
