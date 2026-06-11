package com.codingapi.report;

import com.codingapi.report.register.JdbcTemplateContextRegister;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AutoConfiguration {

    @Configuration
    @ConditionalOnClass(JdbcTemplate.class)
    public static class JdbcTemplateConfiguration{

        @Bean
        public JdbcTemplateContextRegister jdbcTemplateContextRegister(JdbcTemplate jdbcTemplate){
            return new JdbcTemplateContextRegister(jdbcTemplate);
        }

    }



}
