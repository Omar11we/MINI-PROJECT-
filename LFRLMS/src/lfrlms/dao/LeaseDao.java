package lfrlms.dao;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lfrlms.exception.LfrlmsException;
import lfrlms.model.Lease;
import lfrlms.util.QueryBuilder;
import lfrlms.util.QueryLog;

/**
 * Purpose: Reads and writes lease rows without applying lifecycle rules.
 * Concept: JDBC CRUD and server-side pagination.
 * Layer: DAO (data access).
 * Rubric: JDBC CRUD, overloading.
 */
public class LeaseDao extends AbstractDao {
    private static final String LEASE_COLUMNS = "l.lease_id, l.customer_id, l.vehicle_id, "
            + "l.start_date, l.end_date, l.monthly_charge, l.deposit, l.status, "
            + "l.created_at, l.updated_at ";
    private static final String SEARCH_FROM = "FROM lease l "
            + "JOIN customer c ON c.customer_id = l.customer_id "
            + "JOIN vehicle v ON v.vehicle_id = l.vehicle_id "
            + "LEFT JOIN vw_lease_balance lb ON lb.lease_id = l.lease_id ";
    private static final Map<String, String> SORT_COLUMNS = createSortColumns();

    public LeaseDao() {
        super();
    }

    public LeaseDao(QueryLog queryLog) {
        super(queryLog);
    }

    /** Inserts a lease and returns its generated id. */
    public int insert(Lease lease) throws LfrlmsException {
        String sql = "INSERT INTO lease (customer_id, vehicle_id, start_date, end_date, "
                + "monthly_charge, deposit, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        return executeInsert(sql, Integer.valueOf(lease.getCustomerId()),
                Integer.valueOf(lease.getVehicleId()), lease.getStartDate(), lease.getEndDate(),
                lease.getMonthlyCharge(), lease.getDeposit(), lease.getStatus());
    }

    /** Inserts a lease on a caller-owned connection and returns its generated id. */
    public int insert(java.sql.Connection connection, Lease lease)
            throws LfrlmsException {
        String sql = "INSERT INTO lease (customer_id, vehicle_id, start_date, end_date, "
                + "monthly_charge, deposit, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        return executeInsert(connection, sql, Integer.valueOf(lease.getCustomerId()),
                Integer.valueOf(lease.getVehicleId()), lease.getStartDate(), lease.getEndDate(),
                lease.getMonthlyCharge(), lease.getDeposit(), lease.getStatus());
    }

    /** Finds a lease by id, or returns null. */
    public Lease findById(int leaseId) throws LfrlmsException {
        String sql = "SELECT " + LEASE_COLUMNS + "FROM lease l WHERE l.lease_id = ?";
        return toLease(firstRow(queryRows(sql, Integer.valueOf(leaseId))));
    }

    /** Reads a lease and locks its row for the caller's transaction. */
    public Lease lockByIdForUpdate(Connection connection, int leaseId)
            throws LfrlmsException {
        // FOR UPDATE locks this row until the caller commits or rolls back
        String sql = "SELECT " + LEASE_COLUMNS
                + "FROM lease l WHERE l.lease_id = ? FOR UPDATE";
        return toLease(firstRow(queryRows(connection, sql, Integer.valueOf(leaseId))));
    }

    /** Returns one sorted page of leases with no filters. */
    public ArrayList<Lease> findPage(String sortKey, boolean ascending,
            int pageNumber, int pageSize) throws LfrlmsException {
        return search(null, null, null, null, false, sortKey, ascending,
                pageNumber, pageSize);
    }

    /** Searches leases by text, status, start-date range and outstanding balance. */
    public ArrayList<Lease> search(String text, String status, LocalDate from,
            LocalDate to, boolean outstandingOnly, String sortKey, boolean ascending,
            int pageNumber, int pageSize) throws LfrlmsException {
        QueryBuilder query = new QueryBuilder("SELECT " + LEASE_COLUMNS + SEARCH_FROM
                + "WHERE 1 = 1");
        appendLeaseFilters(query, text, status, from, to, outstandingOnly);
        query.appendOrderBy(sortKey, SORT_COLUMNS, "startDate", ascending)
                .appendLimitOffset(pageSize, pageNumber);
        ArrayList<HashMap<String, Object>> rows =
                queryRows(query.getSql(), query.getParameters().toArray());
        ArrayList<Lease> leases = new ArrayList<Lease>();
        for (HashMap<String, Object> row : rows) {
            leases.add(toLease(row));
        }
        return leases;
    }

    /** Counts leases that match the search filters. */
    public long countSearchResults(String text, String status, LocalDate from,
            LocalDate to, boolean outstandingOnly) throws LfrlmsException {
        QueryBuilder query = new QueryBuilder("SELECT COUNT(*) AS row_count "
                + SEARCH_FROM + "WHERE 1 = 1");
        appendLeaseFilters(query, text, status, from, to, outstandingOnly);
        return queryCount(query.getSql(), query.getParameters().toArray());
    }

    /** Counts all leases. */
    public long countAll() throws LfrlmsException {
        return queryCount("SELECT COUNT(*) AS row_count FROM lease");
    }

    /** Updates a lease; returns true if a row was updated. */
    public boolean update(Lease lease) throws LfrlmsException {
        String sql = "UPDATE lease SET customer_id = ?, vehicle_id = ?, start_date = ?, "
                + "end_date = ?, monthly_charge = ?, deposit = ?, status = ? "
                + "WHERE lease_id = ?";
        return executeUpdate(sql, Integer.valueOf(lease.getCustomerId()),
                Integer.valueOf(lease.getVehicleId()), lease.getStartDate(), lease.getEndDate(),
                lease.getMonthlyCharge(), lease.getDeposit(), lease.getStatus(),
                Integer.valueOf(lease.getLeaseId())) > 0;
    }

    /** Updates a lease on a caller-owned connection. */
    public boolean update(java.sql.Connection connection, Lease lease)
            throws LfrlmsException {
        String sql = "UPDATE lease SET customer_id = ?, vehicle_id = ?, start_date = ?, "
                + "end_date = ?, monthly_charge = ?, deposit = ?, status = ? "
                + "WHERE lease_id = ?";
        return executeUpdate(connection, sql, Integer.valueOf(lease.getCustomerId()),
                Integer.valueOf(lease.getVehicleId()), lease.getStartDate(), lease.getEndDate(),
                lease.getMonthlyCharge(), lease.getDeposit(), lease.getStatus(),
                Integer.valueOf(lease.getLeaseId())) > 0;
    }

    /** Deletes a lease by id; returns true if a row was removed. */
    public boolean delete(int leaseId) throws LfrlmsException {
        return executeUpdate("DELETE FROM lease WHERE lease_id = ?",
                Integer.valueOf(leaseId)) > 0;
    }

    /** Deletes a lease on a caller-owned connection. */
    public boolean delete(java.sql.Connection connection, int leaseId)
            throws LfrlmsException {
        return executeUpdate(connection, "DELETE FROM lease WHERE lease_id = ?",
                Integer.valueOf(leaseId)) > 0;
    }

    private void appendLeaseFilters(QueryBuilder query, String text, String status,
            LocalDate from, LocalDate to, boolean outstandingOnly) {
        query.appendLikeAnyCondition(new String[] {"c.first_name", "c.last_name",
                "v.registration_no", "v.model"}, text);
        if (status != null && !status.trim().isEmpty()) {
            query.appendAndCondition("l.status", "=", status.trim());
        }
        if (from != null) {
            query.appendAndCondition("l.start_date", ">=", from);
        }
        if (to != null) {
            query.appendAndCondition("l.start_date", "<", to);
        }
        if (outstandingOnly) {
            query.appendAndFixedCondition("lb.balance_due > 0");
        }
    }

    private Lease toLease(HashMap<String, Object> row) {
        if (row == null) {
            return null;
        }
        Lease lease = new Lease(intValue(row, "lease_id"), intValue(row, "customer_id"),
                intValue(row, "vehicle_id"), dateValue(row, "start_date"),
                dateValue(row, "end_date"), decimalValue(row, "monthly_charge"),
                decimalValue(row, "deposit"), stringValue(row, "status"));
        lease.setCreatedAt(dateTimeValue(row, "created_at"));
        lease.setUpdatedAt(dateTimeValue(row, "updated_at"));
        return lease;
    }

    private static Map<String, String> createSortColumns() {
        HashMap<String, String> columns = new HashMap<String, String>();
        columns.put("startdate", "l.start_date");
        columns.put("enddate", "l.end_date");
        columns.put("status", "l.status");
        columns.put("id", "l.lease_id");
        return columns;
    }
}
