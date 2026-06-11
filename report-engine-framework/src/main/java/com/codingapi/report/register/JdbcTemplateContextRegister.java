package com.codingapi.report.register;

import com.codingapi.report.context.JdbcTemplateContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcTemplateContextRegister {


    public JdbcTemplateContextRegister(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        if(jdbcTemplate!=null) {
            JdbcTemplateContext.getInstance().setJdbcTemplate(jdbcTemplate);
        }
    }
}
