package lfrlms.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import lfrlms.exception.LfrlmsException;
import lfrlms.exception.DataAccessException;
import lfrlms.exception.ValidationException;
import lfrlms.util.QueryLog;

/**
 * Purpose: Reads database metadata and explains logged SELECT statements.
 * Concept: PreparedStatement, metadata and allowlisted identifiers.
 * Layer: DAO (data access).
 * Rubric: JDBC CRUD, Exception handling.
 */
public class InsightsDao extends AbstractDao {
    private static final String[] TABLE_NAMES = new String[] {
        "brand", "vehicle_category", "branch", "customer", "individual_customer",
        "corporate_customer", "driver", "vehicle", "rental", "lease", "payment",
        "maintenance"
    };

    public InsightsDao() {
        super(QueryLog.getShared());
    }

    /** Returns the allowlisted application table names. */
    public ArrayList<String> listTableNames() {
        ArrayList<String> names = new ArrayList<String>();
        for (String tableName : TABLE_NAMES) {
            names.add(tableName);
        }
        return names;
    }

    /** Returns the logged SQL statements, newest first. */
    public ArrayList<QueryLog.QueryEntry> recentQueries() {
        return queryLog.getRecentRecords();
    }

    /** Runs EXPLAIN FORMAT=TREE on a logged SELECT with its original parameters. */
    public ArrayList<HashMap<String, Object>> explain(QueryLog.QueryEntry entry)
            throws LfrlmsException {
        String selectSql = validatedSelect(entry);
        String sql = "EXPLAIN FORMAT=TREE " + selectSql;
        Object[] parameters = entry.getParameterValues();
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParameters(statement, parameters, 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                return readExplainRows(resultSet);
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Could not load the selected query plan.", exception);
        }
    }

    /** Returns the CREATE TABLE statement for an allowlisted table. */
    public String showCreateTable(String tableName) throws LfrlmsException {
        if (!isAllowedTable(tableName)) {
            throw new ValidationException("Choose one of the listed database tables.");
        }
        ArrayList<HashMap<String, Object>> rows = queryRows(
                "SHOW CREATE TABLE `" + tableName + "`");
        HashMap<String, Object> row = firstRow(rows);
        Object definition = row == null ? null : row.get("create table");
        return definition == null ? "Table definition is unavailable." : definition.toString();
    }

    /** Lists the views in the current schema. */
    public ArrayList<HashMap<String, Object>> listViews() throws LfrlmsException {
        String sql = "SELECT TABLE_NAME AS object_name, VIEW_DEFINITION AS definition "
                + "FROM information_schema.VIEWS WHERE TABLE_SCHEMA = DATABASE() "
                + "ORDER BY TABLE_NAME";
        return queryRows(sql);
    }

    /** Lists the stored functions or procedures in the current schema. */
    public ArrayList<HashMap<String, Object>> listRoutines(String routineType)
            throws LfrlmsException {
        if (!"FUNCTION".equals(routineType) && !"PROCEDURE".equals(routineType)) {
            throw new ValidationException("Choose a supported routine type.");
        }
        String sql = "SELECT ROUTINE_NAME AS object_name, ROUTINE_DEFINITION AS definition "
                + "FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = DATABASE() "
                + "AND ROUTINE_TYPE = ? ORDER BY ROUTINE_NAME";
        return queryRows(sql, routineType);
    }

    /** Lists the triggers in the current schema. */
    public ArrayList<HashMap<String, Object>> listTriggers() throws LfrlmsException {
        String sql = "SELECT TRIGGER_NAME AS object_name, "
                + "CONCAT(ACTION_TIMING, ' ', EVENT_MANIPULATION, ' ON ', EVENT_OBJECT_TABLE, "
                + "CHAR(10), ACTION_STATEMENT) AS definition "
                + "FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE() "
                + "ORDER BY TRIGGER_NAME";
        return queryRows(sql);
    }

    private String validatedSelect(QueryLog.QueryEntry entry) throws ValidationException {
        if (entry == null || entry.getSql() == null) {
            throw new ValidationException("Select a logged query first.");
        }
        String sql = entry.getSql().trim();
        String upper = sql.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("SELECT") || (upper.length() > 6
                && !Character.isWhitespace(upper.charAt(6)))) {
            throw new ValidationException("EXPLAIN is available only for logged SELECT queries.");
        }
        int semicolon = sql.indexOf(';');
        if (semicolon >= 0) {
            if (semicolon != sql.length() - 1) {
                throw new ValidationException("Only a single logged SELECT can be explained.");
            }
            sql = sql.substring(0, semicolon).trim();
        }
        return sql;
    }

    private boolean isAllowedTable(String tableName) {
        if (tableName == null) {
            return false;
        }
        for (String allowedName : TABLE_NAMES) {
            if (allowedName.equals(tableName)) {
                return true;
            }
        }
        return false;
    }

    private ArrayList<HashMap<String, Object>> readExplainRows(ResultSet resultSet)
            throws SQLException {
        ArrayList<HashMap<String, Object>> rows = new ArrayList<HashMap<String, Object>>();
        ResultSetMetaData metadata = resultSet.getMetaData();
        int columnCount = metadata.getColumnCount();
        while (resultSet.next()) {
            HashMap<String, Object> row = new HashMap<String, Object>();
            for (int column = 1; column <= columnCount; column++) {
                String label = metadata.getColumnLabel(column).toLowerCase(Locale.ROOT);
                row.put(label, resultSet.getObject(column));
            }
            rows.add(row);
        }
        return rows;
    }
}
