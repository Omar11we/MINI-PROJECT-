package lfrlms.dao;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lfrlms.exception.DataAccessException;
import lfrlms.exception.DuplicateRecordException;
import lfrlms.exception.LfrlmsException;
import lfrlms.exception.RecordInUseException;
import lfrlms.util.DBConnection;
import lfrlms.util.QueryLog;

/**
 * Purpose: Shares JDBC resource handling and error translation across DAOs.
 * Concept: Inheritance, try-with-resources and exception chaining.
 * Layer: DAO (data access).
 * Rubric: interfaces/abstract classes, Exception handling.
 */
public abstract class AbstractDao {
    protected final QueryLog queryLog;

    protected AbstractDao() {
        this(QueryLog.getShared());
    }

    protected AbstractDao(QueryLog queryLog) {
        this.queryLog = queryLog == null ? QueryLog.getShared() : queryLog;
    }

    /** Runs a SELECT on its own connection and returns each row as a column map. */
    protected ArrayList<HashMap<String, Object>> queryRows(String sql,
            Object... parameters) throws LfrlmsException {
        // Start timing for the query log
        long started = System.nanoTime();
        // Open a connection and prepare the SQL; try-with-resources closes both
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            // Bind the values to the ? placeholders
            bindParameters(statement, parameters, 1);
            // Run the query and copy each row into a map
            try (ResultSet resultSet = statement.executeQuery()) {
                ArrayList<HashMap<String, Object>> rows = readRows(resultSet);
                // Log the SQL with its timing and row count
                recordQuery(sql, parameters, started, rows.size());
                return rows;
            }
        } catch (SQLException exception) {
            // Log the failure, then turn the SQLException into an application exception
            recordQuery(sql, parameters, started, -1);
            throw translate(exception, "read database records");
        }
    }

    /** Executes a query on a caller-owned connection without closing that connection. */
    protected ArrayList<HashMap<String, Object>> queryRows(Connection connection,
            String sql, Object... parameters) throws LfrlmsException {
        // Start timing for the query log
        long started = System.nanoTime();
        // Prepare the SQL; only the statement is closed here, not the caller's connection
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            // Bind the values to the ? placeholders
            bindParameters(statement, parameters, 1);
            // Run the query and copy each row into a map
            try (ResultSet resultSet = statement.executeQuery()) {
                ArrayList<HashMap<String, Object>> rows = readRows(resultSet);
                // Log the SQL with its timing and row count
                recordQuery(sql, parameters, started, rows.size());
                return rows;
            }
        } catch (SQLException exception) {
            // Log the failure, then turn the SQLException into an application exception
            recordQuery(sql, parameters, started, -1);
            throw translate(exception, "read database records");
        }
    }

    /** Calls a stored procedure and returns the rows of its result set. */
    protected ArrayList<HashMap<String, Object>> callRows(String sql,
            Object... parameters) throws LfrlmsException {
        long started = System.nanoTime();
        try (Connection connection = openConnection();
                CallableStatement statement = connection.prepareCall(sql)) {
            bindParameters(statement, parameters, 1);
            boolean hasResultSet = statement.execute();
            while (!hasResultSet && statement.getUpdateCount() != -1) {
                hasResultSet = statement.getMoreResults();
            }
            ArrayList<HashMap<String, Object>> rows = new ArrayList<HashMap<String, Object>>();
            if (hasResultSet) {
                try (ResultSet resultSet = statement.getResultSet()) {
                    rows = readRows(resultSet);
                }
            }
            recordQuery(sql, parameters, started, rows.size());
            return rows;
        } catch (SQLException exception) {
            recordQuery(sql, parameters, started, -1);
            throw translate(exception, "run a database procedure");
        }
    }

    /** Calls a stored function and returns its value. */
    protected Object callScalarFunction(String sql, int returnType,
            Object... parameters) throws LfrlmsException {
        long started = System.nanoTime();
        try (Connection connection = openConnection();
                CallableStatement statement = connection.prepareCall(sql)) {
            statement.registerOutParameter(1, returnType);
            bindParameters(statement, parameters, 2);
            statement.execute();
            Object value = statement.getObject(1);
            recordQuery(sql, parameters, started, value == null ? 0 : 1);
            return value;
        } catch (SQLException exception) {
            recordQuery(sql, parameters, started, -1);
            throw translate(exception, "run a database function");
        }
    }

    /** Runs an UPDATE or DELETE on its own connection and returns the affected row count. */
    protected int executeUpdate(String sql, Object... parameters)
            throws LfrlmsException {
        long started = System.nanoTime();
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParameters(statement, parameters, 1);
            int rows = statement.executeUpdate();
            recordQuery(sql, parameters, started, rows);
            return rows;
        } catch (SQLException exception) {
            recordQuery(sql, parameters, started, -1);
            throw translate(exception, "update database records");
        }
    }

    /** Executes a write on a caller-owned connection without closing that connection. */
    protected int executeUpdate(Connection connection, String sql, Object... parameters)
            throws LfrlmsException {
        long started = System.nanoTime();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParameters(statement, parameters, 1);
            int rows = statement.executeUpdate();
            recordQuery(sql, parameters, started, rows);
            return rows;
        } catch (SQLException exception) {
            recordQuery(sql, parameters, started, -1);
            throw translate(exception, "update database records");
        }
    }

    /** Runs an INSERT on its own connection and returns the generated key. */
    protected int executeInsert(String sql, Object... parameters)
            throws LfrlmsException {
        long started = System.nanoTime();
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        sql, Statement.RETURN_GENERATED_KEYS)) {
            bindParameters(statement, parameters, 1);
            int rows = statement.executeUpdate();
            int generatedId = 0;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    generatedId = keys.getInt(1);
                }
            }
            recordQuery(sql, parameters, started, rows);
            return generatedId;
        } catch (SQLException exception) {
            recordQuery(sql, parameters, started, -1);
            throw translate(exception, "insert a database record");
        }
    }

    /** Inserts on a caller-owned connection without closing that connection. */
    protected int executeInsert(Connection connection, String sql, Object... parameters)
            throws LfrlmsException {
        long started = System.nanoTime();
        try (PreparedStatement statement = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            bindParameters(statement, parameters, 1);
            int rows = statement.executeUpdate();
            int generatedId = 0;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    generatedId = keys.getInt(1);
                }
            }
            recordQuery(sql, parameters, started, rows);
            return generatedId;
        } catch (SQLException exception) {
            recordQuery(sql, parameters, started, -1);
            throw translate(exception, "insert a database record");
        }
    }

    /** Runs a COUNT query and returns its first column. */
    protected long queryCount(String sql, Object... parameters)
            throws LfrlmsException {
        ArrayList<HashMap<String, Object>> rows = queryRows(sql, parameters);
        if (rows.isEmpty()) {
            return 0L;
        }
        Object value = rows.get(0).values().iterator().next();
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    /** Runs a COUNT query on a caller-owned connection and returns its first column. */
    protected long queryCount(Connection connection, String sql, Object... parameters)
            throws LfrlmsException {
        ArrayList<HashMap<String, Object>> rows = queryRows(connection, sql, parameters);
        if (rows.isEmpty()) {
            return 0L;
        }
        Object value = rows.get(0).values().iterator().next();
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    /** Opens a new connection and translates setup failures. */
    protected Connection openConnection() throws LfrlmsException {
        try {
            return DBConnection.getConnection();
        } catch (SQLException exception) {
            throw translate(exception, "connect to the database");
        } catch (IOException exception) {
            SQLException wrapped = new SQLException("Database configuration could not be loaded.",
                    exception);
            throw new DataAccessException("Database configuration is unavailable.", wrapped);
        }
    }

    /** Binds each value to its placeholder, converting java.time values to SQL types. */
    protected void bindParameters(PreparedStatement statement, Object[] parameters,
            int firstIndex) throws SQLException {
        for (int offset = 0; offset < parameters.length; offset++) {
            Object value = parameters[offset];
            int index = firstIndex + offset;
            if (value == null) {
                statement.setNull(index, Types.NULL);
            } else if (value instanceof LocalDate) {
                statement.setDate(index, Date.valueOf((LocalDate) value));
            } else if (value instanceof LocalDateTime) {
                statement.setTimestamp(index, Timestamp.valueOf((LocalDateTime) value));
            } else {
                statement.setObject(index, value);
            }
        }
    }

    /** Returns the first row, or null if there are none. */
    protected HashMap<String, Object> firstRow(ArrayList<HashMap<String, Object>> rows) {
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Reads a numeric column as an int, or zero if it is null. */
    protected int intValue(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    /** Reads a numeric column as an Integer, or null. */
    protected Integer nullableIntValue(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value instanceof Number ? Integer.valueOf(((Number) value).intValue()) : null;
    }

    /** Reads a column as text, or null. */
    protected String stringValue(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? null : value.toString();
    }

    /** Reads a numeric column as a BigDecimal, or null. */
    protected BigDecimal decimalValue(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return value instanceof Number
                ? new BigDecimal(value.toString()) : null;
    }

    /** Reads a DATE column as a LocalDate, or null. */
    protected LocalDate dateValue(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        return value instanceof Date ? ((Date) value).toLocalDate() : null;
    }

    /** Reads a DATETIME column as a LocalDateTime, or null. */
    protected LocalDateTime dateTimeValue(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        return value instanceof Timestamp ? ((Timestamp) value).toLocalDateTime() : null;
    }

    private ArrayList<HashMap<String, Object>> readRows(ResultSet resultSet)
            throws SQLException {
        ArrayList<HashMap<String, Object>> rows = new ArrayList<HashMap<String, Object>>();
        ResultSetMetaData metadata = resultSet.getMetaData();
        int columnCount = metadata.getColumnCount();
        while (resultSet.next()) {
            HashMap<String, Object> row = new HashMap<String, Object>();
            for (int column = 1; column <= columnCount; column++) {
                row.put(metadata.getColumnLabel(column).toLowerCase(),
                        resultSet.getObject(column));
            }
            rows.add(row);
        }
        return rows;
    }

    private void recordQuery(String sql, Object[] parameters, long started, int rows) {
        long elapsedMillis = (System.nanoTime() - started) / 1000000L;
        queryLog.add(sql, parameters, elapsedMillis, rows);
    }

    private LfrlmsException translate(SQLException exception, String action) {
        // MySQL error 1451: another table still references this row
        if (exception.getErrorCode() == 1451) {
            return new RecordInUseException("Deactivate it instead", exception);
        }
        // MySQL error 1062: duplicate unique key; use a friendly message for known keys
        if (exception.getErrorCode() == 1062 && "23000".equals(exception.getSQLState())) {
            String duplicateMessage = duplicateMessage(exception);
            if (duplicateMessage != null) {
                return new DuplicateRecordException(duplicateMessage, exception);
            }
        }
        // Anything else becomes a general data access error
        return new DataAccessException("Could not " + action + ".", exception);
    }

    private String duplicateMessage(SQLException exception) {
        SQLException current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String key = message.toLowerCase(java.util.Locale.ROOT);
                if (key.contains("uq_individual_license")) {
                    return "A customer with this driving licence number already exists.";
                }
                if (key.contains("uq_customer_email")) {
                    return "A customer with this email address already exists.";
                }
                if (key.contains("uq_corporate_customer_gst")) {
                    return "A customer with this GST number already exists.";
                }
            }
            current = current.getNextException();
        }
        return null;
    }
}
