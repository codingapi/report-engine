package com.codingapi.report.context;

import com.codingapi.report.data.DataResult;
import com.codingapi.report.data.DataRow;
import com.codingapi.report.meta.DBTable;
import com.codingapi.report.scanner.DBScanner;
import lombok.Getter;
import lombok.Setter;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

public class JdbcTemplateContext {

    @Getter
    private final static JdbcTemplateContext instance = new JdbcTemplateContext();

    private JdbcTemplateContext() {
    }

    @Setter
    private JdbcTemplate jdbcTemplate;


    /**
     * 要扫描的表
     *
     * @param targetScanners 表名称
     * @return 表元数据信息
     */
    public List<DBTable> scannerMeta(List<String> targetScanners) throws SQLException {
        if (this.jdbcTemplate != null && targetScanners != null) {
            DataSource dataSource = jdbcTemplate.getDataSource();
            if (dataSource != null) {
                Connection connection = dataSource.getConnection();
                DBScanner dbScanner = new DBScanner(connection, targetScanners);
                return dbScanner.loadTables();
            }
        }
        return null;
    }

    public DataResult queryForList(String sql, Object... args) {
        return new DataResult(this.jdbcTemplate.query(sql, (rs, rowNum) -> {
            DataRow dataRow = new DataRow();
            ResultSetMetaData metaData = rs.getMetaData();
            int count = metaData.getColumnCount();
            for (int i = 1; i <= count; i++) {
                dataRow.addItem(metaData.getColumnName(i), rs.getObject(i));
            }
            return dataRow;
        }, args));
    }


}
