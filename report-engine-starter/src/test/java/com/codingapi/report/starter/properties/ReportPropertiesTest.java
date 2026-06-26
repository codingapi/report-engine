package com.codingapi.report.starter.properties;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/** ReportProperties 绑定测试：验证默认值与配置键绑定（前缀 codingapi.report，子配置 font/driver/excel/csv）。 */
class ReportPropertiesTest {

    private ReportProperties bind(Map<String, String> props) {
        StandardEnvironment env = new StandardEnvironment();
        Map<String, Object> map = new LinkedHashMap<>(props);
        env.getPropertySources().addFirst(new MapPropertySource("test", map));
        return Binder.get(env).bindOrCreate("codingapi.report", ReportProperties.class);
    }

    @Test
    void defaultsWhenUnconfigured() {
        ReportProperties p = bind(Map.of());
        assertNotNull(p.getFont());
        assertNull(p.getFont().getDir());
        assertEquals("./data/drivers", p.getDriver().getDir());
        assertEquals("./data/excel", p.getExcel().getDir());
        assertEquals("./data/csv", p.getCsv().getDir());
    }

    @Test
    void bindsFontDir() {
        ReportProperties p = bind(Map.of("codingapi.report.font.dir", "/custom/fonts"));
        assertEquals("/custom/fonts", p.getFont().getDir());
    }

    @Test
    void bindsAllSubDirs() {
        ReportProperties p =
                bind(
                        Map.of(
                                "codingapi.report.font.dir", "/fonts",
                                "codingapi.report.driver.dir", "/drivers",
                                "codingapi.report.excel.dir", "/excel",
                                "codingapi.report.csv.dir", "/csv"));
        assertEquals("/fonts", p.getFont().getDir());
        assertEquals("/drivers", p.getDriver().getDir());
        assertEquals("/excel", p.getExcel().getDir());
        assertEquals("/csv", p.getCsv().getDir());
    }

    @Test
    void emptyFontDirBindsAsNull() {
        ReportProperties p = bind(Map.of("codingapi.report.font.dir", ""));
        assertNotNull(p.getFont());
        assertEquals("", p.getFont().getDir());
    }
}
