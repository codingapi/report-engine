package com.codingapi.report.starter;

import com.codingapi.report.excel.FontRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
