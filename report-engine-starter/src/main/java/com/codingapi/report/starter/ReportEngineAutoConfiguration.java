package com.codingapi.report.starter;

import com.codingapi.report.data.datasource.DataExtractor;
import com.codingapi.report.data.datasource.credential.CredentialService;
import com.codingapi.report.excel.FontRegistry;
import com.codingapi.report.repository.DataModelRepository;
import com.codingapi.report.repository.DataSourceTypeRepository;
import com.codingapi.report.repository.ReportRepository;
import com.codingapi.report.starter.controller.DataModelMgmtController;
import com.codingapi.report.starter.controller.DataSourceController;
import com.codingapi.report.starter.controller.DataSourceTypeController;
import com.codingapi.report.starter.controller.DatasetController;
import com.codingapi.report.starter.controller.ExcelController;
import com.codingapi.report.starter.controller.ExpressionController;
import com.codingapi.report.starter.controller.FontController;
import com.codingapi.report.starter.controller.ReportConfigController;
import com.codingapi.report.starter.controller.ReportRenderController;
import com.codingapi.report.starter.properties.ReportProperties;
import com.codingapi.report.starter.service.DataModelService;
import com.codingapi.report.starter.service.DataSourceService;
import com.codingapi.report.starter.service.DataSourceTypeService;
import com.codingapi.report.starter.service.DriverLoader;
import com.codingapi.report.starter.service.ReportConfigService;
import com.codingapi.report.starter.service.ReportRenderService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;

/**
 * Report Engine 自动配置。
 *
 * <p>自动装配 {@link FontRegistry} Bean： 提取内置字体 → 合并用户自定义字体 → 扫描 → 注册到 JVM
 *
 * <p>Web 环境下注册全部通用 REST API Controller（数据源管理 / 报表配置 / 渲染 / 数据集 / 字体 / 公式 / Excel）， 业务下沉 service
 * 层（{@code com.codingapi.report.starter.service}），Controller 只做 HTTP 编排。
 */
@Configuration
@EnableConfigurationProperties(ReportProperties.class)
public class ReportEngineAutoConfiguration {

    @Bean
    public FontRegistry fontRegistry(ReportProperties properties) throws IOException {
        Path builtinDir = FontRegistry.extractBuiltinFonts();

        Path customDir = null;
        String fontDir = properties.getFont().getDir();
        if (fontDir != null && !fontDir.isBlank()) {
            customDir = Path.of(fontDir);
        }

        FontRegistry registry =
                (customDir != null)
                        ? new FontRegistry(builtinDir, customDir)
                        : new FontRegistry(builtinDir);

        registry.scanDirectory();
        registry.registerToGraphicsEnvironment();
        return registry;
    }

    /** Web 环境下的自动配置：注册 service 与 REST API Controller。 */
    @Configuration
    @ConditionalOnClass(RestController.class)
    static class WebConfiguration {

        // ─── service 层 ───────────────────────────────────
        @Bean
        @ConditionalOnMissingBean
        public DataModelService dataModelService(
                DataModelRepository dataModelRepository, CredentialService credentials) {
            return new DataModelService(dataModelRepository, credentials);
        }

        @Bean
        @ConditionalOnMissingBean
        public DataSourceService dataSourceService(
                DataModelService dataModelService, List<DataExtractor> extractors) {
            return new DataSourceService(dataModelService, extractors);
        }

        @Bean
        @ConditionalOnMissingBean
        public ReportConfigService reportConfigService(
                ReportRepository repository, DataModelService dataModelService) {
            return new ReportConfigService(repository, dataModelService);
        }

        @Bean
        @ConditionalOnMissingBean
        public ReportRenderService reportRenderService(
                DataModelService dataModelService, List<DataExtractor> extractors) {
            return new ReportRenderService(dataModelService, extractors);
        }

        @Bean
        @ConditionalOnMissingBean
        public DataSourceTypeService dataSourceTypeService(
                DataSourceTypeRepository repository, ReportProperties properties) {
            return new DataSourceTypeService(repository, properties);
        }

        @Bean
        @ConditionalOnMissingBean
        public DriverLoader driverLoader(
                DataSourceTypeRepository repository, ReportProperties properties) {
            return new DriverLoader(repository, properties);
        }

        // ─── Controller 层 ────────────────────────────────
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
        public ReportRenderController reportRenderController(ReportRenderService renderService) {
            return new ReportRenderController(renderService);
        }

        @Bean
        @ConditionalOnMissingBean
        public DatasetController datasetController(
                DataModelService dataModelService, DataSourceService dataSourceService) {
            return new DatasetController(dataModelService, dataSourceService);
        }

        @Bean
        @ConditionalOnMissingBean
        public ExpressionController expressionController() {
            return new ExpressionController();
        }

        @Bean
        @ConditionalOnMissingBean
        public ReportConfigController reportConfigController(
                ReportConfigService reportConfigService) {
            return new ReportConfigController(reportConfigService);
        }

        @Bean
        @ConditionalOnMissingBean
        public DataModelMgmtController dataModelMgmtController(DataModelService dataModelService) {
            return new DataModelMgmtController(dataModelService);
        }

        @Bean
        @ConditionalOnMissingBean
        public DataSourceController dataSourceController(DataSourceService dataSourceService) {
            return new DataSourceController(dataSourceService);
        }

        @Bean
        @ConditionalOnMissingBean
        public DataSourceTypeController dataSourceTypeController(
                DataSourceTypeService dataSourceTypeService) {
            return new DataSourceTypeController(dataSourceTypeService);
        }
    }
}
