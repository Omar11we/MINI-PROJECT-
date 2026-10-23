package lfrlms.service;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import lfrlms.exception.DataAccessException;
import lfrlms.exception.LfrlmsException;
import lfrlms.util.DBConnection;

// Layer: Service (business logic).
// Rubric: interfaces/abstract classes, Exception handling.
/** Shared transaction helpers for services; services own and close their connections. */
abstract class AbstractService {
    /** Opens a connection that the calling service must close. */
    protected Connection openConnection() throws LfrlmsException {
        try {
            return DBConnection.getConnection();
        } catch (SQLException exception) {
            throw new DataAccessException("Could not connect to the database.", exception);
        } catch (IOException exception) {
            SQLException wrapped = new SQLException(
                    "Database configuration could not be loaded.", exception);
            throw new DataAccessException("Database configuration is unavailable.", wrapped);
        }
    }

    /** Turns off auto-commit so the next statements form one transaction. */
    protected void beginTransaction(Connection connection) throws LfrlmsException {
        try {
            connection.setAutoCommit(false);
        } catch (SQLException exception) {
            throw new DataAccessException("Could not start the database transaction.", exception);
        }
    }

    /** Commits the transaction, rolling back if the commit fails. */
    protected void commit(Connection connection) throws LfrlmsException {
        try {
            connection.commit();
        } catch (SQLException exception) {
            rollback(connection, exception);
            throw new DataAccessException("Could not commit the database transaction.", exception);
        }
    }

    /** Rolls back, attaching any rollback failure to the original error. */
    protected void rollback(Connection connection, Throwable originalFailure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
        }
    }

    /** Wraps an SQL error in a DataAccessException. */
    protected LfrlmsException databaseFailure(String action, SQLException exception) {
        return new DataAccessException("Could not " + action + ".", exception);
    }
}
