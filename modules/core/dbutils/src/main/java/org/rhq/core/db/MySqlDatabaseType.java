package org.rhq.core.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class MySqlDatabaseType extends DatabaseType {

    private static final String SELECT_SEQUENCE = "SELECT AUTO_INCREMENT FROM information_schema.tables WHERE table_name = '%s' and table_schema = database() limit 1";

    /**
     * The vendor name for all MySql databases.
     */
    public static final String VENDOR_NAME = "mysql";

    /**
     * Returns {@link #VENDOR_NAME}.
     *
     * @see DatabaseType#getVendor()
     */
    public String getVendor() {
        return VENDOR_NAME;
    }

    public String getHibernateDialect() {
        return "org.hibernate.dialect.MySQLDialect";
    }
    /**
     * @see DatabaseType#isTableNotFoundException(SQLException)
     */
    public boolean isTableNotFoundException(SQLException e) {
        return (e.getErrorCode() == 1146);
    }

    /**
     * In MySQL, there is no sequence, use aotu_increment instead. In this way, if more than one columns are sequence_only will not be supported.
     * @see DatabaseType#getSequenceValue(Connection, String, String)
     */
    public int getSequenceValue(Connection conn, String table, String key) throws SQLException {
        String query = String.format(SELECT_SEQUENCE, table);
        PreparedStatement selectPS = null;
        ResultSet rs = null;

        try {
            selectPS = conn.prepareStatement(query);
            rs = selectPS.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }

            throw new RuntimeException(DbUtilsI18NFactory.getMsg()
                .getMsg(DbUtilsI18NResourceKeys.NOT_A_SEQUENCE, query));
        } finally {
            closeJDBCObjects(null, selectPS, rs);
        }
    }

    public String getSequenceInsertValue(Connection conn, String sequenceName) {
        return "null";
    }

    /**
     * @see DatabaseType#getNextSequenceValue(Connection, String, String)
     */
    public int getNextSequenceValue(Connection conn, String table, String key) throws SQLException {
        int sequenceValue = getSequenceValue(conn, table, key);
        return sequenceValue + 1;
    }

    /**
     * @see DatabaseType#alterColumn(Connection, String, String, String, String, String, Boolean, Boolean)
     */
    public void alterColumn(Connection conn, String table, String column, String generic_column_type,
        String default_value, String precision, Boolean nullable, Boolean reindex) throws SQLException {
        String db_column_type = null;
        String sql = "ALTER TABLE " + table;

        boolean execute = true;
        if (generic_column_type != null) {
            db_column_type = getDBTypeFromGenericType(generic_column_type);
            if (precision != null) {
                db_column_type += " (" + precision + ")";
            }
            sql += " MODIFY " + column + " " + db_column_type;

            if (default_value != null) {
                sql += " DEFAULT '" + default_value + "'";
            }

            if (nullable != null) {
                sql += (nullable.booleanValue() ? " NULL" : " NOT NULL");
            }
        } else if (default_value != null && nullable == null) {
            sql += " ALTER " + column + " SET DEFAULT '" + default_value + "'";
        } else {
            execute = false;
            System.err.print("Cannot alter column in MySQL when generic column type is not specified.");
            //TODO Since there are test suite altering column without specifying column type. Just ignore it.
        }

        if (execute) {
            executeSql(conn, sql);
        }

        // now that we've altered the column, let's reindex if we were told to do so
        if ((reindex != null) && reindex.booleanValue()) {
            reindexTable(conn, table);
        }

        return;
    }

    /**
     * override, MySQL didn't create sequence, and do nothing when drop sequence.
     */
    public void dropSequence(Connection conn, String sequence_name) throws SQLException {
    }

    /**
     * @see DatabaseType#reindexTable(Connection, String)
     */
    public void reindexTable(Connection conn, String table) throws SQLException {
        String engine = System.getProperty("mysql.engine", "InnoDB");
        String reindexSql = "ALTER TABLE " + table + " ENGINE=" + engine;
        executeSql(conn, reindexSql);
    }

    @Override
    public boolean supportsSelfReferringCascade() {
        return false;
    }

    public String getEscapeCharacter() {
        String result = System.getProperty("rhq.server.database.escape-character");
        return (null == result) ? "/" : result;
    }

}