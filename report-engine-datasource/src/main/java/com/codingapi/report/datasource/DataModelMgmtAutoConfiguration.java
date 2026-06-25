package com.codingapi.report.datasource;

import com.codingapi.report.data.datasource.DataExtractor;
import com.codingapi.report.data.datasource.csv.CsvDataExtractor;
import com.codingapi.report.datasource.converter.DataModelConfigConverter;
import com.codingapi.report.datasource.credential.CredentialService;
import com.codingapi.report.datasource.db.DbDataExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 数据源管理自动配置：注册能力 Bean（凭证服务、DTO 转换器、各 {@link DataExtractor} 实现）。
 *
 * <p>本模块只提供能力，不提供 REST API——管理 Controller 归 starter（"全部通用 REST API 在 starter"的架构约定）， 由 starter 的
 * {@code ReportEngineAutoConfiguration} 装配并注入这里的能力 Bean。
 */
@Configuration
public class DataModelMgmtAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CredentialService credentialService(
            @Value("${report.datasource.crypto.key:}") String key) {
        return new CredentialService(key);
    }

    @Bean
    @ConditionalOnMissingBean
    public DataModelConfigConverter dataModelConfigConverter(CredentialService credentials) {
        return new DataModelConfigConverter(credentials);
    }

    @Bean
    @ConditionalOnMissingBean
    public DbDataExtractor dbDataExtractor() {
        return new DbDataExtractor();
    }

    /** CSV 提取器（framework 内置，Bean 注册下放至此，example 不再单独声明）。 */
    @Bean
    @ConditionalOnMissingBean
    public CsvDataExtractor csvDataExtractor() {
        return new CsvDataExtractor();
    }
}
