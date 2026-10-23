package lfrlms.dao;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lfrlms.exception.LfrlmsException;
import lfrlms.model.Rental;
import lfrlms.util.QueryBuilder;
import lfrlms.util.QueryLog;

/**
 * Purpose: Reads and writes rental rows and calls the completion procedure.
 * Concept: JDBC CRUD, callable statements and paginated search.
 * Layer: DAO (data access).
 * Rubric: JDBC CRUD, overloading.
 */
public class RentalDao extends AbstractDao {
    private static final String RENTAL_COLUMNS = "r.rental_id, r.customer_id, r.vehicle_id, "
            + "r.driver_id, r.scheduled_start_datetime, r.expected_return_datetime, "
            + "r.actual_pickup_datetime, r.actual_return_datetime, r.odometer_out, "
            + "r.odometer_in, r.condition_out, r.condition_in, r.daily_rate_applied, "
            + "r.charges, r.status, r.created_at, r.updated_at ";
    private static final String SEARCH_FROM = "FROM rental r "
            + "JOIN customer c ON c.customer_id = r.customer_id "
            + "JOIN vehicle v ON v.vehicle_id = r.vehicle_id "
            + "LEFT JOIN vw_rental_balance rb ON rb.rental_id = r.rental_id ";
    private static final Map<String, String> SORT_COLUMNS = createSortColumns();

    public RentalDao() {
        super();
    }

    public RentalDao(QueryLog queryLog) {
        super(queryLog);
    }

    /** Inserts a rental and returns its generated id. */
    public int insert(Rental rental) throws LfrlmsException {
        String sql = "INSERT INTO rental (customer_id, vehicle_id, driver_id, "
                + "scheduled_start_datetime, expected_return_datetime, actual_pickup_datetime, "
                + "actual_return_datetime, odometer_out, odometer_in, condition_out, "
                + "condition_in, daily_rate_applied, charges, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return executeInsert(sql, Integer.valueOf(rental.getCustomerId()),
                Integer.valueOf(rental.getVehicleId()), rental.getDriverId(),
                rental.getScheduledStartDatetime(), rental.getExpectedReturnDatetime(),
                rental.getActualPickupDatetime(), rental.getActualReturnDatetime(),
                rental.getOdometerOut(), rental.getOdometerIn(), rental.getConditionOut(),
                rental.getConditionIn(), rental.getDailyRateApplied(), rental.getCharges(),
                rental.getStatus());
    }

    /** Inserts a rental on a caller-owned connection and returns its generated id. */
    public int insert(java.sql.Connection connection, Rental rental)
            throws LfrlmsException {
        String sql = "INSERT INTO rental (customer_id, vehicle_id, driver_id, "
                + "scheduled_start_datetime, expected_return_datetime, actual_pickup_datetime, "
                + "actual_return_datetime, odometer_out, odometer_in, condition_out, "
                + "condition_in, daily_rate_applied, charges, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return executeInsert(connection, sql, Integer.valueOf(rental.getCustomerId()),
                Integer.valueOf(rental.getVehicleId()), rental.getDriverId(),
                rental.getScheduledStartDatetime(), rental.getExpectedReturnDatetime(),
                rental.getActualPickupDatetime(), rental.getActualReturnDatetime(),
                rental.getOdometerOut(), rental.getOdometerIn(), rental.getConditionOut(),
                rental.getConditionIn(), rental.getDailyRateApplied(), rental.getCharges(),
                rental.getStatus());
    }

    /** Finds a rental by id, or returns null. */
    public Rental findById(int rentalId) throws LfrlmsException {
        String sql = "SELECT " + RENTAL_COLUMNS + "FROM rental r WHERE r.rental_id = ?";
        return toRental(firstRow(queryRows(sql, Integer.valueOf(rentalId))));
    }

    /** Reads a rental and locks its row for the caller's transaction. */
    public Rental lockByIdForUpdate(Connection connection, int rentalId)
            throws LfrlmsException {
        // FOR UPDATE locks this row until the caller commits or rolls back
        String sql = "SELECT " + RENTAL_COLUMNS
                + "FROM rental r WHERE r.rental_id = ? FOR UPDATE";
        return toRental(firstRow(queryRows(connection, sql, Integer.valueOf(rentalId))));
    }

    /** Lists a vehicle's future confirmed rentals, at most 100. */
    public ArrayList<Rental> findUpcomingForVehicle(int vehicleId, int limit)
            throws LfrlmsException {
        String sql = "SELECT " + RENTAL_COLUMNS + "FROM rental r "
                + "WHERE r.vehicle_id = ? AND r.status = 'Confirmed' "
                + "AND r.scheduled_start_datetime >= NOW() "
                + "ORDER BY r.scheduled_start_datetime, r.rental_id LIMIT ?";
        int safeLimit = limit < 1 ? 1 : Math.min(limit, 100);
        ArrayList<HashMap<String, Object>> rows = queryRows(sql,
                Integer.valueOf(vehicleId), Integer.valueOf(safeLimit));
        ArrayList<Rental> rentals = new ArrayList<Rental>();
        for (HashMap<String, Object> row : rows) {
            rentals.add(toRental(row));
        }
        return rentals;
    }

    /** Returns one sorted page of rentals with no filters. */
    public ArrayList<Rental> findPage(String sortKey, boolean ascending,
            int pageNumber, int pageSize) throws LfrlmsException {
        return search(null, null, null, null, false, sortKey, ascending,
                pageNumber, pageSize);
    }

    /** Searches rentals by text, status, scheduled-start range and outstanding balance. */
    public ArrayList<Rental> search(String text, String status,
            LocalDateTime from, LocalDateTime to, boolean outstandingOnly,
            String sortKey, boolean ascending, int pageNumber, int pageSize)
            throws LfrlmsException {
        QueryBuilder query = new QueryBuilder("SELECT " + RENTAL_COLUMNS + SEARCH_FROM
                + "WHERE 1 = 1");
        appendRentalFilters(query, text, status, from, to, outstandingOnly);
        query.appendOrderBy(sortKey, SORT_COLUMNS, "scheduledStart", ascending)
                .appendLimitOffset(pageSize, pageNumber);
        ArrayList<HashMap<String, Object>> rows =
                queryRows(query.getSql(), query.getParameters().toArray());
        ArrayList<Rental> rentals = new ArrayList<Rental>();
        for (HashMap<String, Object> row : rows) {
            rentals.add(toRental(row));
        }
        return rentals;
    }

    /** Counts rentals that match the search filters. */
    public long countSearchResults(String text, String status,
            LocalDateTime from, LocalDateTime to, boolean outstandingOnly)
            throws LfrlmsException {
        QueryBuilder query = new QueryBuilder("SELECT COUNT(*) AS row_count "
                + SEARCH_FROM + "WHERE 1 = 1");
        appendRentalFilters(query, text, status, from, to, outstandingOnly);
        return queryCount(query.getSql(), query.getParameters().toArray());
    }

    /** Counts all rentals. */
    public long countAll() throws LfrlmsException {
        return queryCount("SELECT COUNT(*) AS row_count FROM rental");
    }

    /** Updates a rental; returns true if a row was updated. */
    public boolean update(Rental rental) throws LfrlmsException {
        String sql = "UPDATE rental SET customer_id = ?, vehicle_id = ?, driver_id = ?, "
                + "scheduled_start_datetime = ?, expected_return_datetime = ?, "
                + "actual_pickup_datetime = ?, actual_return_datetime = ?, odometer_out = ?, "
                + "odometer_in = ?, condition_out = ?, condition_in = ?, "
                + "daily_rate_applied = ?, charges = ?, status = ? WHERE rental_id = ?";
        return executeUpdate(sql, Integer.valueOf(rental.getCustomerId()),
                Integer.valueOf(rental.getVehicleId()), rental.getDriverId(),
                rental.getScheduledStartDatetime(), rental.getExpectedReturnDatetime(),
                rental.getActualPickupDatetime(), rental.getActualReturnDatetime(),
                rental.getOdometerOut(), rental.getOdometerIn(), rental.getConditionOut(),
                rental.getConditionIn(), rental.getDailyRateApplied(), rental.getCharges(),
                rental.getStatus(), Integer.valueOf(rental.getRentalId())) > 0;
    }

    /** Updates a rental on a caller-owned connection. */
    public boolean update(java.sql.Connection connection, Rental rental)
            throws LfrlmsException {
        String sql = "UPDATE rental SET customer_id = ?, vehicle_id = ?, driver_id = ?, "
                + "scheduled_start_datetime = ?, expected_return_datetime = ?, "
                + "actual_pickup_datetime = ?, actual_return_datetime = ?, odometer_out = ?, "
                + "odometer_in = ?, condition_out = ?, condition_in = ?, "
                + "daily_rate_applied = ?, charges = ?, status = ? WHERE rental_id = ?";
        return executeUpdate(connection, sql, Integer.valueOf(rental.getCustomerId()),
                Integer.valueOf(rental.getVehicleId()), rental.getDriverId(),
                rental.getScheduledStartDatetime(), rental.getExpectedReturnDatetime(),
                rental.getActualPickupDatetime(), rental.getActualReturnDatetime(),
                rental.getOdometerOut(), rental.getOdometerIn(), rental.getConditionOut(),
                rental.getConditionIn(), rental.getDailyRateApplied(), rental.getCharges(),
                rental.getStatus(), Integer.valueOf(rental.getRentalId())) > 0;
    }

    /** Deletes a rental by id; returns true if a row was removed. */
    public boolean delete(int rentalId) throws LfrlmsException {
        return executeUpdate("DELETE FROM rental WHERE rental_id = ?",
                Integer.valueOf(rentalId)) > 0;
    }

    /** Deletes a rental on a caller-owned connection. */
    public boolean delete(java.sql.Connection connection, int rentalId)
            throws LfrlmsException {
        return executeUpdate(connection, "DELETE FROM rental WHERE rental_id = ?",
                Integer.valueOf(rentalId)) > 0;
    }

    /** Completes a rental by calling the sp_complete_rental procedure. */
    public void completeRental(int rentalId, LocalDateTime returnDateTime,
            int odometerIn, String conditionIn) throws LfrlmsException {
        String call = "{call sp_complete_rental(?, ?, ?, ?)}";
        callRows(call, Integer.valueOf(rentalId), returnDateTime,
                Integer.valueOf(odometerIn), conditionIn);
    }

    private void appendRentalFilters(QueryBuilder query, String text, String status,
            LocalDateTime from, LocalDateTime to, boolean outstandingOnly) {
        query.appendLikeAnyCondition(new String[] {"c.first_name", "c.last_name",
                "v.registration_no", "v.model"}, text);
        if (status != null && !status.trim().isEmpty()) {
            query.appendAndCondition("r.status", "=", status.trim());
        }
        if (from != null) {
            query.appendAndCondition("r.scheduled_start_datetime", ">=", from);
        }
        if (to != null) {
            query.appendAndCondition("r.scheduled_start_datetime", "<", to);
        }
        if (outstandingOnly) {
            query.appendAndFixedCondition("rb.balance_due > 0");
        }
    }

    private Rental toRental(HashMap<String, Object> row) {
        if (row == null) {
            return null;
        }
        Rental rental = new Rental(intValue(row, "rental_id"),
                intValue(row, "customer_id"), intValue(row, "vehicle_id"),
                nullableIntValue(row, "driver_id"),
                dateTimeValue(row, "scheduled_start_datetime"),
                dateTimeValue(row, "expected_return_datetime"),
                dateTimeValue(row, "actual_pickup_datetime"),
                dateTimeValue(row, "actual_return_datetime"),
                nullableIntValue(row, "odometer_out"), nullableIntValue(row, "odometer_in"),
                stringValue(row, "condition_out"), stringValue(row, "condition_in"),
                decimalValue(row, "daily_rate_applied"), decimalValue(row, "charges"),
                stringValue(row, "status"));
        rental.setCreatedAt(dateTimeValue(row, "created_at"));
        rental.setUpdatedAt(dateTimeValue(row, "updated_at"));
        return rental;
    }

    private static Map<String, String> createSortColumns() {
        HashMap<String, String> columns = new HashMap<String, String>();
        columns.put("scheduledstart", "r.scheduled_start_datetime");
        columns.put("expectedreturn", "r.expected_return_datetime");
        columns.put("status", "r.status");
        columns.put("id", "r.rental_id");
        return columns;
    }
}
