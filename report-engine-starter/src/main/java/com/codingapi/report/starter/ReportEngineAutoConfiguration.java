package com.codingapi.report.starter;

import com.codingapi.report.data.datamodel.DataModel;
import com.codingapi.report.data.datasource.csv.CsvDataExtractor;
import com.codingapi.report.excel.FontRegistry;
import com.codingapi.report.starter.controller.DatasetController;
import com.codingapi.report.starter.controller.ExcelController;
import com.codingapi.report.starter.controller.ExpressionController;
import com.codingapi.report.starter.controller.FontController;
import com.codingapi.report.starter.controller.ReportConfigController;
import com.codingapi.report.starter.controller.ReportRenderController;
import com.codingapi.report.repository.InMemoryReportRepository;
import com.codingapi.report.repository.ReportRepository;
import com.codingapi.report.starter.properties.ReportFontProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

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

    /** 报表配置存储：默认 framework 内存实现，使用方可提供持久化实现覆盖。 */
    @Bean
    @ConditionalOnMissingBean
    public ReportRepository reportRepository() {
        return new InMemoryReportRepository();
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

        @Bean
        @ConditionalOnMissingBean
        public ReportRenderController reportRenderController(DataModel dataModel, CsvDataExtractor csvExtractor) {
            return new ReportRenderController(dataModel, csvExtractor);
        }

        @Bean
        @ConditionalOnMissingBean
        public DatasetController datasetController(DataModel dataModel, CsvDataExtractor csvExtractor) {
            return new DatasetController(dataModel, csvExtractor);
        }

        @Bean
        @ConditionalOnMissingBean
        public ExpressionController expressionController() {
            return new ExpressionController();
        }

        @Bean
        @ConditionalOnMissingBean
        public ReportConfigController reportConfigController(ReportRepository repository, DataModel dataModel) {
            return new ReportConfigController(repository, dataModel);
        }
    }
}
