package com.codingapi.report.data.datasource.extractor;

import com.codingapi.report.data.dataset.DataType;
import com.codingapi.report.data.dataset.Dataset;
import com.codingapi.report.data.dataset.Field;
import com.codingapi.report.data.datasource.ColumnMeta;
import com.codingapi.report.data.datasource.DataExtractor;
import com.codingapi.report.data.datasource.DataSource;
import com.codingapi.report.data.datasource.IntrospectedTable;
import com.codingapi.report.data.datasource.RawTable;
import com.codingapi.report.data.datasource.TestResult;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * 关系型数据库提取器：统一走 JDBC，厂商差异（MySQL/Postgres/Oracle/H2）落在 {@code DataSource.config} 的 url/driver
 * 里，不构成类型差异（与 {@code DB} 类型"按取数方式划分"一致）。
 *
 * <h3>连接配置约定</h3>
 *
 * <ul>
 *   <li>{@code url}：完整 JDBC URL（如 {@code jdbc:h2:mem:test}），优先使用
 *   <li>否则用 {@code host}/{@code port}/{@code database} 拼接（driver 必填或从 url 推断）
 *   <li>{@code driver}：JDBC 驱动类名（如 {@code org.h2.Driver}）
 *   <li>{@code username}/{@code password}：凭证（password 在持久态为加密值，由 converter 解密后传入）
 * </ul>
 *
 * <p>连接用 {@code DriverManager}，用完即关（一期不做连接池）。{@code sourceTable} 可为表名或一段 SELECT SQL （以 {@code
 * SELECT} 开头当 SQL 直接执行，否则 {@code SELECT * FROM <table>}）。
 */
@Slf4j
public class DbDataExtractor implements DataExtractor {

    @Override
    public boolean supports(String type) {
        return "DB".equals(type);
    }

    @Override
    public RawTable extract(DataSource source, Dataset dataset) {
        String sql = buildQuery(source, dataset);
        log.debug("DB 提取: {} sql={}", dataset.getId(), sql);
        try (Connection conn = openConnection(source);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            return readRows(rs, dataset);
        } catch (SQLException e) {
            throw new IllegalStateException("DB 提取失败: " + dataset.getId(), e);
        }
    }

    @Override
    public TestResult test(DataSource source) {
        long start = System.currentTimeMillis();
        try (Connection conn = openConnection(source);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT 1")) {
            rs.next();
            long latency = System.currentTimeMillis() - start;
            return new TestResult(true, "连接成功", latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new TestResult(false, "连接失败: " + e.getMessage(), latency);
        }
    }

    @Override
    public List<String> listTables(DataSource source) {
        return new ArrayList<>(tableRemarks(source).keySet());
    }

    /** 表名 → 表备注（JDBC {@code getTables} 的 REMARKS 列），无备注为 null。listTables 与 introspect 共用，避免重复建连。 */
    private Map<String, String> tableRemarks(DataSource source) {
        Map<String, String> remarks = new LinkedHashMap<>();
        try (Connection conn = openConnection(source)) {
            DatabaseMetaData md = conn.getMetaData();
            try (ResultSet rs = md.getTables(null, null, "%", new String[] {"TABLE"})) {
                while (rs.next()) {
                    remarks.put(rs.getString("TABLE_NAME"), rs.getString("REMARKS"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("表探查失败", e);
        }
        return remarks;
    }

    @Override
    public List<ColumnMeta> listColumns(DataSource source, String table) {
        Set<String> pk = new HashSet<>();
        List<ColumnMeta> columns = new ArrayList<>();
        try (Connection conn = openConnection(source)) {
            DatabaseMetaData md = conn.getMetaData();
            try (ResultSet rs = md.getPrimaryKeys(null, null, table)) {
                while (rs.next()) {
                    pk.add(rs.getString("COLUMN_NAME"));
                }
            }
            try (ResultSet rs = md.getColumns(null, null, table, "%")) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    String type = rs.getString("TYPE_NAME");
                    String remark = rs.getString("REMARKS");
                    columns.add(new ColumnMeta(name, type, pk.contains(name), remark));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("列探查失败: " + table, e);
        }
        return columns;
    }

    @Override
    public List<IntrospectedTable> introspect(DataSource source) {
        List<IntrospectedTable> tables = new ArrayList<>();
        for (Map.Entry<String, String> e : tableRemarks(source).entrySet()) {
            tables.add(new IntrospectedTable(e.getKey(), listColumns(source, e.getKey()), e.getValue()));
        }
        return tables;
    }

    // ============================================================
    // 内部
    // ============================================================

    private String buildQuery(DataSource source, Dataset dataset) {
        if (dataset instanceof com.codingapi.report.data.dataset.TableDataset t) {
            String table = t.getSourceTable();
            if (table == null || table.isBlank()) {
                throw new IllegalStateException("数据集缺少 sourceTable: " + dataset.getId());
            }
            String trimmed = table.trim();
            if (trimmed.toUpperCase().startsWith("SELECT")) {
                return trimmed;
            }
            return "SELECT * FROM " + table;
        }
        throw new IllegalStateException("DB 提取只支持物理表数据集: " + dataset.getId());
    }

    private RawTable readRows(ResultSet rs, Dataset dataset) throws SQLException {
        List<String> columns = new ArrayList<>();
        for (Field f : dataset.getFields()) {
            columns.add(dataset.getId() + "." + f.getName());
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Field f : dataset.getFields()) {
                Object raw = rs.getObject(f.getName());
                row.put(dataset.getId() + "." + f.getName(), coerce(raw, f.getDataType()));
            }
            rows.add(row);
        }
        return new RawTable(columns, rows);
    }

    private static Object coerce(Object value, DataType type) {
        if (value == null) return null;
        return switch (type) {
            case NUMBER ->
                    value instanceof Number n
                            ? n.doubleValue()
                            : Double.parseDouble(String.valueOf(value));
            case BOOLEAN ->
                    value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
            default -> String.valueOf(value);
        };
    }

    private Connection openConnection(DataSource source) throws SQLException {
        Map<String, Object> config =
                source.getConfig() != null ? source.getConfig() : new HashMap<>();
        String url = resolveUrl(config);
        String driver = String.valueOf(config.getOrDefault("driver", ""));
        if (!driver.isBlank() && !"null".equals(driver)) {
            try {
                Class.forName(driver);
            } catch (ClassNotFoundException e) {
                throw new SQLException("驱动未找到: " + driver, e);
            }
        }
        String user = config.get("username") instanceof String u ? u : null;
        String pass = config.get("password") instanceof String p ? p : null;
        return DriverManager.getConnection(url, user, pass);
    }

    /** 优先用 {@code url}；否则 host/port/database 拼接（driver 用于推断方言）。 */
    private String resolveUrl(Map<String, Object> config) {
        Object urlObj = config.get("url");
        if (urlObj instanceof String u && !u.isBlank()) {
            return u;
        }
        String host = String.valueOf(config.getOrDefault("host", "localhost"));
        String port = config.get("port") == null ? "" : String.valueOf(config.get("port"));
        String db = String.valueOf(config.getOrDefault("database", ""));
        String driver = String.valueOf(config.getOrDefault("driver", "")).toLowerCase();
        if (driver.contains("mysql")) {
            return "jdbc:mysql://" + host + (port.isBlank() ? "" : ":" + port) + "/" + db;
        }
        if (driver.contains("postgresql")) {
            return "jdbc:postgresql://" + host + (port.isBlank() ? "" : ":" + port) + "/" + db;
        }
        if (driver.contains("h2")) {
            return "jdbc:h2:" + (db.isBlank() ? "mem:test" : db);
        }
        throw new IllegalStateException("无法解析 JDBC URL：请直接配置 config.url");
    }
}
