package com.codingapi.report.scanner;

import com.codingapi.report.meta.DBColumn;
import com.codingapi.report.meta.DBTable;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DBScanner {

    private final DatabaseMetaData metaData;
    private final String catalog;
    private final String schema;

    private final List<String> scanners;

    private final List<DBTable> tables;

    public DBScanner(Connection connection,List<String> scanners) throws SQLException {
        this.tables = new ArrayList<>();
        this.scanners = scanners;
        this.metaData = connection.getMetaData();
        this.catalog = connection.getCatalog();
        this.schema = connection.getSchema();
    }


    /**
     * 是否需要扫描
     * @param tableName 表名称
     */
    private boolean isScanner(String tableName){
        for (String scanner:this.scanners){
            if(scanner.equalsIgnoreCase(tableName)){
                return true;
            }
        }
        return false;
    }

    /**
     * 扫描数据库中所有表的元数据，返回列表（不创建新的 DBMetaData 对象）
     *
     * @throws SQLException SQLException
     */
    public void scanAllTables() throws SQLException {
        ResultSet tables = metaData.getTables(catalog, schema, "%", new String[]{"TABLE"});
        while (tables.next()) {
            String tableName = tables.getString("TABLE_NAME");
            String remarks = tables.getString("REMARKS");
            if(this.isScanner(tableName)) {
                DBTable table = new DBTable(tableName, remarks);
                this.loadTable(tableName, table);
                this.tables.add(table);
            }
        }
        tables.close();
    }

    /**
     * 扫描数据库中的所有表、字段和主键信息，并缓存
     */
    public List<DBTable> loadTables() throws SQLException {
        this.scanAllTables();
        return this.tables;
    }


    private void loadTable(String tableName, DBTable table) throws SQLException {
        // 获取列信息
        ResultSet columns = metaData.getColumns(catalog, schema, tableName, "%");
        while (columns.next()) {
            DBColumn column = new DBColumn();
            column.setName(columns.getString("COLUMN_NAME"));
            column.setDescription(columns.getString("REMARKS"));
            column.setDataType(DataTypeConvertor.toType(columns.getInt("DATA_TYPE")));
            table.addColumn(column);
        }
        columns.close();

        // 获取主键信息
        ResultSet pkRs = metaData.getPrimaryKeys(catalog, schema, tableName);
        while (pkRs.next()) {
            String pkColumn = pkRs.getString("COLUMN_NAME");
            table.addPrimaryKey(pkColumn);
        }
        pkRs.close();

        ResultSet importedKeys =  metaData.getImportedKeys(catalog,schema,tableName);
        while (importedKeys.next()) {
            String fkColumnName = importedKeys.getString("FKCOLUMN_NAME");
            String pkColumn = importedKeys.getString("PKCOLUMN_NAME");
            String pkTableName = importedKeys.getString("PKTABLE_NAME");

            DBColumn dbColumn = table.getColumnByName(fkColumnName);
            if(dbColumn!=null){
                dbColumn.addForeignKey(pkTableName,pkColumn);
            }
        }

        importedKeys.close();
    }
}
