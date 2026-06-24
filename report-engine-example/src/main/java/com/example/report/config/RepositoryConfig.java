package com.example.report.config;

import com.codingapi.report.repository.ReportRepository;
import com.example.report.repository.InMemoryReportRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 报表配置存储：example 演示用内存实现。
 * <p>
 * 生产环境应由使用方提供持久化实现覆盖此 Bean。
 */
@Configuration
public class RepositoryConfig {

    @Bean
    public ReportRepository reportRepository() {
        return new InMemoryReportRepository();
    }
}
