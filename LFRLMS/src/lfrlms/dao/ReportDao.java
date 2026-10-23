package lfrlms.dao;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;
import lfrlms.exception.LfrlmsException;
import lfrlms.util.QueryLog;

/**
 * Purpose: Runs the parameterized report queries listed in the project spec.
 * Concept: JDBC reports, callable statements, aggregation and TreeMap.
 * Layer: DAO (data access).
 * Rubric: JDBC CRUD, Collections.
 */
public class ReportDao extends AbstractDao {
    private static final String ACTIVE_RENTALS_FROM = "FROM rental r "
            + "JOIN customer c ON c.customer_id = r.customer_id "
            + "JOIN vehicle v ON v.vehicle_id = r.vehicle_id WHERE r.status = ? ";
    private static final String ACTIVE_LEASES_FROM = "FROM lease l "
            + "JOIN customer c ON c.customer_id = l.customer_id "
            + "JOIN vehicle v ON v.vehicle_id = l.vehicle_id WHERE l.status = ? ";
    private static final String CUSTOMER_HISTORY_ROWS =
            "SELECT 'Rental' AS transaction_type, r.rental_id AS reference_id, "
            + "r.scheduled_start_datetime AS transaction_date, v.registration_no, "
            + "r.status, r.charges AS amount FROM rental r "
            + "JOIN vehicle v ON v.vehicle_id = r.vehicle_id WHERE r.customer_id = ? "
            + "UNION ALL SELECT 'Lease', l.lease_id, CAST(l.start_date AS DATETIME), "
            + "v.registration_no, l.status, l.monthly_charge FROM lease l "
            + "JOIN vehicle v ON v.vehicle_id = l.vehicle_id WHERE l.customer_id = ? "
            + "UNION ALL SELECT 'Payment', p.payment_id, p.payment_date, "
            + "COALESCE(v.registration_no, ''), p.status, p.amount FROM payment p "
            + "LEFT JOIN rental r ON r.rental_id = p.rental_id "
            + "LEFT JOIN lease l ON l.lease_id = p.lease_id "
            + "LEFT JOIN vehicle v ON v.vehicle_id = COALESCE(r.vehicle_id, l.vehicle_id) "
            + "WHERE COALESCE(r.customer_id, l.customer_id) = ?";

    public ReportDao() {
        super();
    }

    public ReportDao(QueryLog queryLog) {
        super(queryLog);
    }

    /** Lists vehicles free for a period using the sp_available_vehicles procedure. */
    public ArrayList<HashMap<String, Object>> availableVehicles(LocalDateTime from,
            LocalDateTime to, Integer branchId, Integer categoryId)
            throws LfrlmsException {
        return callRows("{call sp_available_vehicles(?, ?, ?, ?)}",
                from, to, branchId, categoryId);
    }

    /** Returns one page of active rentals with customer and vehicle details. */
    public ArrayList<HashMap<String, Object>> activeRentals(int pageNumber,
            int pageSize) throws LfrlmsException {
        String sql = "SELECT r.rental_id, c.customer_id, c.first_name, c.last_name, "
                + "v.vehicle_id, v.registration_no, v.model, r.scheduled_start_datetime, "
                + "r.expected_return_datetime, r.actual_pickup_datetime, r.status "
                + ACTIVE_RENTALS_FROM + "ORDER BY r.scheduled_start_datetime DESC "
                + "LIMIT ? OFFSET ?";
        return queryRows(sql, "Active", safePageSize(pageSize), pageOffset(pageNumber, pageSize));
    }

    /** Counts active rentals. */
    public long countActiveRentals() throws LfrlmsException {
        return queryCount("SELECT COUNT(*) AS row_count " + ACTIVE_RENTALS_FROM, "Active");
    }

    /** Returns one page of active leases, earliest end date first. */
    public ArrayList<HashMap<String, Object>> activeLeases(int pageNumber,
            int pageSize) throws LfrlmsException {
        String sql = "SELECT l.lease_id, c.customer_id, c.first_name, c.last_name, "
                + "v.vehicle_id, v.registration_no, v.model, l.start_date, l.end_date, "
                + "l.monthly_charge, l.deposit, l.status " + ACTIVE_LEASES_FROM
                + "ORDER BY l.end_date, l.lease_id LIMIT ? OFFSET ?";
        return queryRows(sql, "Active", safePageSize(pageSize), pageOffset(pageNumber, pageSize));
    }

    /** Counts active leases. */
    public long countActiveLeases() throws LfrlmsException {
        return queryCount("SELECT COUNT(*) AS row_count " + ACTIVE_LEASES_FROM, "Active");
    }

    /** Totals successful payments per month in a date range, sorted by month. */
    public TreeMap<String, BigDecimal> revenueByMonth(LocalDate from, LocalDate to)
            throws LfrlmsException {
        String sql = "SELECT DATE_FORMAT(payment_date, '%Y-%m') AS revenue_month, "
                + "SUM(amount) AS revenue FROM payment WHERE status = ? "
                + "AND payment_date >= ? AND payment_date < DATE_ADD(?, INTERVAL 1 DAY) "
                + "GROUP BY DATE_FORMAT(payment_date, '%Y-%m') "
                + "ORDER BY DATE_FORMAT(payment_date, '%Y-%m')";
        ArrayList<HashMap<String, Object>> rows = queryRows(sql, "Success", from, to);
        TreeMap<String, BigDecimal> revenue = new TreeMap<String, BigDecimal>();
        for (HashMap<String, Object> row : rows) {
            revenue.put(stringValue(row, "revenue_month"), decimalValue(row, "revenue"));
        }
        return revenue;
    }

    /** Returns one page of customers with their outstanding balance. */
    public ArrayList<HashMap<String, Object>> outstandingBalances(int pageNumber,
            int pageSize) throws LfrlmsException {
        String sql = "SELECT customer_id, customer_type, first_name, last_name "
                + "FROM customer ORDER BY last_name, first_name LIMIT ? OFFSET ?";
        ArrayList<HashMap<String, Object>> customers = queryRows(sql,
                safePageSize(pageSize), pageOffset(pageNumber, pageSize));
        ArrayList<HashMap<String, Object>> balances =
                new ArrayList<HashMap<String, Object>>();
        for (HashMap<String, Object> customer : customers) {
            BigDecimal amount = customerOutstanding(intValue(customer, "customer_id"));
            customer.put("outstanding", amount);
            balances.add(customer);
        }
        return balances;
    }

    /** Counts the customers listed in the outstanding-balance report. */
    public long countOutstandingBalances() throws LfrlmsException {
        return queryCount("SELECT COUNT(*) AS row_count FROM customer");
    }

    /** Ranks vehicles by their number of active and completed rentals. */
    public ArrayList<HashMap<String, Object>> mostRentedVehicles(int limit)
            throws LfrlmsException {
        String sql = "SELECT v.vehicle_id, v.registration_no, b.brand_name, v.model, "
                + "COUNT(r.rental_id) AS rental_count FROM vehicle v "
                + "JOIN brand b ON b.brand_id = v.brand_id "
                + "LEFT JOIN rental r ON r.vehicle_id = v.vehicle_id "
                + "AND r.status IN ('Active', 'Completed') "
                + "GROUP BY v.vehicle_id, v.registration_no, b.brand_name, v.model "
                + "ORDER BY rental_count DESC, v.vehicle_id LIMIT ?";
        return queryRows(sql, Integer.valueOf(safePageSize(limit)));
    }

    /** Returns one page of maintenance whose next service date is on or before a date. */
    public ArrayList<HashMap<String, Object>> maintenanceDue(LocalDate throughDate,
            int pageNumber, int pageSize) throws LfrlmsException {
        String sql = "SELECT m.maintenance_id, m.vehicle_id, v.registration_no, v.model, "
                + "m.service_type, m.next_service_date, m.status FROM maintenance m "
                + "JOIN vehicle v ON v.vehicle_id = m.vehicle_id "
                + "WHERE m.next_service_date IS NOT NULL AND m.next_service_date <= ? "
                + "ORDER BY m.next_service_date, m.maintenance_id LIMIT ? OFFSET ?";
        return queryRows(sql, throughDate, safePageSize(pageSize),
                pageOffset(pageNumber, pageSize));
    }

    /** Counts maintenance whose next service date is on or before a date. */
    public long countMaintenanceDue(LocalDate throughDate) throws LfrlmsException {
        String sql = "SELECT COUNT(*) AS row_count FROM maintenance m "
                + "JOIN vehicle v ON v.vehicle_id = m.vehicle_id "
                + "WHERE m.next_service_date IS NOT NULL AND m.next_service_date <= ?";
        return queryCount(sql, throughDate);
    }

    /** Returns one page of a customer's rentals, leases and payments, newest first. */
    public ArrayList<HashMap<String, Object>> customerTransactionHistory(int customerId,
            int pageNumber, int pageSize) throws LfrlmsException {
        String sql = "SELECT * FROM (" + CUSTOMER_HISTORY_ROWS
                + ") history ORDER BY transaction_date DESC, transaction_type "
                + "LIMIT ? OFFSET ?";
        return queryRows(sql, Integer.valueOf(customerId), Integer.valueOf(customerId),
                Integer.valueOf(customerId), Integer.valueOf(safePageSize(pageSize)),
                Long.valueOf(pageOffset(pageNumber, pageSize)));
    }

    /** Counts a customer's rentals, leases and payments. */
    public long countCustomerTransactionHistory(int customerId) throws LfrlmsException {
        String sql = "SELECT COUNT(*) AS row_count FROM (" + CUSTOMER_HISTORY_ROWS
                + ") history";
        return queryCount(sql, Integer.valueOf(customerId), Integer.valueOf(customerId),
                Integer.valueOf(customerId));
    }

    /** Counts rentals grouped by status. */
    public ArrayList<HashMap<String, Object>> rentalStatusSummary()
            throws LfrlmsException {
        String sql = "SELECT status, COUNT(*) AS rental_count FROM rental "
                + "GROUP BY status ORDER BY status";
        return queryRows(sql);
    }

    /** Returns a customer's balance from fn_customer_outstanding. */
    public BigDecimal customerOutstanding(int customerId) throws LfrlmsException {
        String sql = "{? = call fn_customer_outstanding(?)}";
        Object value = callScalarFunction(sql, Types.DECIMAL, Integer.valueOf(customerId));
        return value instanceof BigDecimal ? (BigDecimal) value : BigDecimal.ZERO;
    }

    /** Calculates a rental charge with fn_rental_charge. */
    public BigDecimal rentalCharge(LocalDateTime start, LocalDateTime end,
            BigDecimal dailyRate) throws LfrlmsException {
        String sql = "{? = call fn_rental_charge(?, ?, ?)}";
        Object value = callScalarFunction(sql, Types.DECIMAL, start, end, dailyRate);
        return value instanceof BigDecimal ? (BigDecimal) value : BigDecimal.ZERO;
    }

    /** Calculates a lease total with fn_lease_total. */
    public BigDecimal leaseTotal(BigDecimal monthlyCharge, LocalDate start,
            LocalDate end) throws LfrlmsException {
        String sql = "{? = call fn_lease_total(?, ?, ?)}";
        Object value = callScalarFunction(sql, Types.DECIMAL,
                monthlyCharge, start, end);
        return value instanceof BigDecimal ? (BigDecimal) value : BigDecimal.ZERO;
    }

    private int safePageSize(int pageSize) {
        return pageSize < 1 ? 1 : pageSize;
    }

    private long pageOffset(int pageNumber, int pageSize) {
        int safePage = pageNumber < 1 ? 1 : pageNumber;
        return ((long) safePage - 1L) * safePageSize(pageSize);
    }
}
