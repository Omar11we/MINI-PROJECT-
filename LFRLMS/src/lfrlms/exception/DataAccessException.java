package lfrlms.exception;

import java.sql.SQLException;

/**
 * Purpose: Wraps a database error while preserving its original cause.
 * Concept: Exception chaining.
 * Layer: Exception.
 * Rubric: Exception handling, overloading.
 */
public class DataAccessException extends LfrlmsException {
    private static final long serialVersionUID = 1L;

    public DataAccessException() {
        super("A database operation failed.");
    }

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(SQLException cause) {
        super("A database operation failed.", cause);
    }

    public DataAccessException(String message, SQLException cause) {
        super(message, cause);
    }
}
