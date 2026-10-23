package lfrlms.dao;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lfrlms.exception.LfrlmsException;
import lfrlms.model.Payment;
import lfrlms.util.QueryBuilder;
import lfrlms.util.QueryLog;

/**
 * Purpose: Reads and writes payment rows without calculating business balances.
 * Concept: JDBC CRUD, nullable references and server-side pagination.
 * Layer: DAO (data access).
 * Rubric: JDBC CRUD, overloading.
 */
public class PaymentDao extends AbstractDao {
    private static final String PAYMENT_COLUMNS = "p.payment_id, p.rental_id, p.lease_id, "
            + "p.amount, p.method, p.payment_date, p.status, p.created_at, p.updated_at ";
    private static final String SEARCH_FROM = "FROM payment p "
            + "LEFT JOIN rental r ON r.rental_id = p.rental_id "
            + "LEFT JOIN lease l ON l.lease_id = p.lease_id "
            + "LEFT JOIN customer c ON c.customer_id = COALESCE(r.customer_id, l.customer_id) "
            + "LEFT JOIN vehicle v ON v.vehicle_id = COALESCE(r.vehicle_id, l.vehicle_id) "
            + "LEFT JOIN vw_rental_balance rb ON rb.rental_id = p.rental_id "
            + "LEFT JOIN vw_lease_balance lb ON lb.lease_id = p.lease_id ";
    private static final Map<String, String> SORT_COLUMNS = createSortColumns();

    public PaymentDao() {
        super();
    }

    public PaymentDao(QueryLog queryLog) {
        super(queryLog);
    }

    /** Inserts a payment and returns its generated id. */
    public int insert(Payment payment) throws LfrlmsException {
        String sql = "INSERT INTO payment (rental_id, lease_id, amount, method, "
                + "payment_date, status) VALUES (?, ?, ?, ?, ?, ?)";
        return executeInsert(sql, payment.getRentalId(), payment.getLeaseId(),
                payment.getAmount(), payment.getMethod(), payment.getPaymentDate(),
                payment.getStatus());
    }

    /** Inserts a payment on a caller-owned connection and returns its generated id. */
    public int insert(java.sql.Connection connection, Payment payment)
            throws LfrlmsException {
        String sql = "INSERT INTO payment (rental_id, lease_id, amount, method, "
                + "payment_date, status) VALUES (?, ?, ?, ?, ?, ?)";
        return executeInsert(connection, sql, payment.getRentalId(), payment.getLeaseId(),
                payment.getAmount(), payment.getMethod(), payment.getPaymentDate(),
                payment.getStatus());
    }

    /** Finds a payment by id, or returns null. */
    public Payment findById(int paymentId) throws LfrlmsException {
        String sql = "SELECT " + PAYMENT_COLUMNS + "FROM payment p WHERE p.payment_id = ?";
        return toPayment(firstRow(queryRows(sql, Integer.valueOf(paymentId))));
    }

    /** Returns one sorted page of payments with no filters. */
    public ArrayList<Payment> findPage(String sortKey, boolean ascending,
            int pageNumber, int pageSize) throws LfrlmsException {
        return search(null, null, null, null, false, sortKey, ascending,
                pageNumber, pageSize);
    }

    /** Searches payments by text, status, payment-date range and outstanding balance. */
    public ArrayList<Payment> search(String text, String status,
            LocalDateTime from, LocalDateTime to, boolean outstandingOnly,
            String sortKey, boolean ascending, int pageNumber, int pageSize)
            throws LfrlmsException {
        QueryBuilder query = new QueryBuilder("SELECT " + PAYMENT_COLUMNS + SEARCH_FROM
                + "WHERE 1 = 1");
        appendPaymentFilters(query, text, status, from, to, outstandingOnly);
        query.appendOrderBy(sortKey, SORT_COLUMNS, "paymentDate", ascending)
                .appendLimitOffset(pageSize, pageNumber);
        ArrayList<HashMap<String, Object>> rows =
                queryRows(query.getSql(), query.getParameters().toArray());
        ArrayList<Payment> payments = new ArrayList<Payment>();
        for (HashMap<String, Object> row : rows) {
            payments.add(toPayment(row));
        }
        return payments;
    }

    /** Counts payments that match the search filters. */
    public long countSearchResults(String text, String status,
            LocalDateTime from, LocalDateTime to, boolean outstandingOnly)
            throws LfrlmsException {
        QueryBuilder query = new QueryBuilder("SELECT COUNT(*) AS row_count "
                + SEARCH_FROM + "WHERE 1 = 1");
        appendPaymentFilters(query, text, status, from, to, outstandingOnly);
        return queryCount(query.getSql(), query.getParameters().toArray());
    }

    /** Counts all payments. */
    public long countAll() throws LfrlmsException {
        return queryCount("SELECT COUNT(*) AS row_count FROM payment");
    }

    /** Updates a payment; returns true if a row was updated. */
    public boolean update(Payment payment) throws LfrlmsException {
        String sql = "UPDATE payment SET rental_id = ?, lease_id = ?, amount = ?, method = ?, "
                + "payment_date = ?, status = ? WHERE payment_id = ?";
        return executeUpdate(sql, payment.getRentalId(), payment.getLeaseId(),
                payment.getAmount(), payment.getMethod(), payment.getPaymentDate(),
                payment.getStatus(), Integer.valueOf(payment.getPaymentId())) > 0;
    }

    /** Updates a payment on a caller-owned connection. */
    public boolean update(java.sql.Connection connection, Payment payment)
            throws LfrlmsException {
        String sql = "UPDATE payment SET rental_id = ?, lease_id = ?, amount = ?, method = ?, "
                + "payment_date = ?, status = ? WHERE payment_id = ?";
        return executeUpdate(connection, sql, payment.getRentalId(), payment.getLeaseId(),
                payment.getAmount(), payment.getMethod(), payment.getPaymentDate(),
                payment.getStatus(), Integer.valueOf(payment.getPaymentId())) > 0;
    }

    /** Deletes a payment by id; returns true if a row was removed. */
    public boolean delete(int paymentId) throws LfrlmsException {
        return executeUpdate("DELETE FROM payment WHERE payment_id = ?",
                Integer.valueOf(paymentId)) > 0;
    }

    /** Deletes a payment on a caller-owned connection. */
    public boolean delete(java.sql.Connection connection, int paymentId)
            throws LfrlmsException {
        return executeUpdate(connection, "DELETE FROM payment WHERE payment_id = ?",
                Integer.valueOf(paymentId)) > 0;
    }

    private void appendPaymentFilters(QueryBuilder query, String text, String status,
            LocalDateTime from, LocalDateTime to, boolean outstandingOnly) {
        query.appendLikeAnyCondition(new String[] {"c.first_name", "c.last_name",
                "v.registration_no", "v.model"}, text);
        if (status != null && !status.trim().isEmpty()) {
            query.appendAndCondition("p.status", "=", status.trim());
        }
        if (from != null) {
            query.appendAndCondition("p.payment_date", ">=", from);
        }
        if (to != null) {
            query.appendAndCondition("p.payment_date", "<", to);
        }
        if (outstandingOnly) {
            query.appendAndFixedCondition("COALESCE(rb.balance_due, lb.balance_due) > 0");
        }
    }

    private Payment toPayment(HashMap<String, Object> row) {
        if (row == null) {
            return null;
        }
        Payment payment = new Payment(intValue(row, "payment_id"),
                nullableIntValue(row, "rental_id"), nullableIntValue(row, "lease_id"),
                decimalValue(row, "amount"), stringValue(row, "method"),
                dateTimeValue(row, "payment_date"), stringValue(row, "status"));
        payment.setCreatedAt(dateTimeValue(row, "created_at"));
        payment.setUpdatedAt(dateTimeValue(row, "updated_at"));
        return payment;
    }

    private static Map<String, String> createSortColumns() {
        HashMap<String, String> columns = new HashMap<String, String>();
        columns.put("paymentdate", "p.payment_date");
        columns.put("amount", "p.amount");
        columns.put("status", "p.status");
        columns.put("id", "p.payment_id");
        return columns;
    }
}
