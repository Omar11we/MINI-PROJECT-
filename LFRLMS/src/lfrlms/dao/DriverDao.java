package lfrlms.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lfrlms.exception.LfrlmsException;
import lfrlms.model.Driver;
import lfrlms.util.QueryBuilder;
import lfrlms.util.QueryLog;

/**
 * Purpose: Reads and writes driver rows without applying assignment rules.
 * Concept: JDBC CRUD and server-side pagination.
 * Layer: DAO (data access).
 * Rubric: JDBC CRUD, Inheritance.
 */
public class DriverDao extends AbstractDao {
    private static final Map<String, String> SORT_COLUMNS = createSortColumns();

    public DriverDao() {
        super();
    }

    public DriverDao(QueryLog queryLog) {
        super(queryLog);
    }

    /** Inserts a driver and returns its generated id. */
    public int insert(Driver driver) throws LfrlmsException {
        String sql = "INSERT INTO driver (first_name, last_name, phone, license_no, status) "
                + "VALUES (?, ?, ?, ?, ?)";
        return executeInsert(sql, driver.getFirstName(), driver.getLastName(),
                driver.getPhone(), driver.getLicenseNo(), driver.getStatus());
    }

    /** Finds a driver by id, or returns null. */
    public Driver findById(int driverId) throws LfrlmsException {
        String sql = "SELECT driver_id, first_name, last_name, phone, license_no, status "
                + "FROM driver WHERE driver_id = ?";
        return toDriver(firstRow(queryRows(sql, Integer.valueOf(driverId))));
    }

    /** Returns one sorted page of drivers. */
    public ArrayList<Driver> findPage(String sortKey, boolean ascending,
            int pageNumber, int pageSize) throws LfrlmsException {
        QueryBuilder query = new QueryBuilder("SELECT driver_id, first_name, last_name, "
                + "phone, license_no, status FROM driver WHERE 1 = 1");
        query.appendOrderBy(sortKey, SORT_COLUMNS, "lastName", ascending)
                .appendLimitOffset(pageSize, pageNumber);
        ArrayList<HashMap<String, Object>> rows =
                queryRows(query.getSql(), query.getParameters().toArray());
        ArrayList<Driver> drivers = new ArrayList<Driver>();
        for (HashMap<String, Object> row : rows) {
            drivers.add(toDriver(row));
        }
        return drivers;
    }

    /** Counts all drivers. */
    public long countAll() throws LfrlmsException {
        return queryCount("SELECT COUNT(*) AS row_count FROM driver");
    }

    /** Updates a driver; returns true if a row was updated. */
    public boolean update(Driver driver) throws LfrlmsException {
        String sql = "UPDATE driver SET first_name = ?, last_name = ?, phone = ?, "
                + "license_no = ?, status = ? WHERE driver_id = ?";
        return executeUpdate(sql, driver.getFirstName(), driver.getLastName(), driver.getPhone(),
                driver.getLicenseNo(), driver.getStatus(),
                Integer.valueOf(driver.getDriverId())) > 0;
    }

    /** Deletes a driver by id; returns true if a row was removed. */
    public boolean delete(int driverId) throws LfrlmsException {
        return executeUpdate("DELETE FROM driver WHERE driver_id = ?",
                Integer.valueOf(driverId)) > 0;
    }

    private Driver toDriver(HashMap<String, Object> row) {
        if (row == null) {
            return null;
        }
        return new Driver(intValue(row, "driver_id"), stringValue(row, "first_name"),
                stringValue(row, "last_name"), stringValue(row, "phone"),
                stringValue(row, "license_no"), stringValue(row, "status"));
    }

    private static Map<String, String> createSortColumns() {
        HashMap<String, String> columns = new HashMap<String, String>();
        columns.put("firstname", "first_name");
        columns.put("lastname", "last_name");
        columns.put("status", "status");
        columns.put("id", "driver_id");
        return columns;
    }
}
