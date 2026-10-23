package lfrlms.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lfrlms.exception.LfrlmsException;
import lfrlms.model.Vehicle;
import lfrlms.util.QueryBuilder;
import lfrlms.util.QueryLog;

/**
 * Purpose: Searches and persists vehicle rows using parameterized SQL.
 * Concept: PreparedStatement, pagination and an ORDER BY whitelist.
 * Layer: DAO (data access).
 * Rubric: JDBC CRUD, overloading.
 */
public class VehicleDao extends AbstractDao {
    private static final String DISPLAY_STATUS_SQL = "CASE WHEN v.status = 'Available' "
            + "AND EXISTS (SELECT 1 FROM rental upcoming WHERE "
            + "upcoming.vehicle_id = v.vehicle_id AND upcoming.status = 'Confirmed' "
            + "AND upcoming.scheduled_start_datetime >= NOW() "
            + "AND upcoming.scheduled_start_datetime < DATE_ADD(NOW(), INTERVAL 1 DAY)) "
            + "THEN 'Reserved' ELSE v.status END";
    private static final String VEHICLE_SELECT = "SELECT v.vehicle_id, v.registration_no, "
            + "v.brand_id, v.category_id, v.branch_id, v.model, v.model_year, "
            + "v.rental_rate, v.lease_rate, v.status, "
            + DISPLAY_STATUS_SQL + " AS display_status, v.created_at, v.updated_at "
            + "FROM vehicle v JOIN brand b ON b.brand_id = v.brand_id "
            + "JOIN vehicle_category vc ON vc.category_id = v.category_id "
            + "JOIN branch br ON br.branch_id = v.branch_id ";
    private static final Map<String, String> SORT_COLUMNS = createSortColumns();

    public VehicleDao() {
        super();
    }

    public VehicleDao(QueryLog queryLog) {
        super(queryLog);
    }

    /** Inserts a vehicle and returns its generated id. */
    public int insert(Vehicle vehicle) throws LfrlmsException {
        String sql = "INSERT INTO vehicle (registration_no, brand_id, category_id, branch_id, "
                + "model, model_year, rental_rate, lease_rate, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return executeInsert(sql, vehicle.getRegistrationNo(),
                Integer.valueOf(vehicle.getBrandId()), Integer.valueOf(vehicle.getCategoryId()),
                Integer.valueOf(vehicle.getBranchId()), vehicle.getModel(),
                Integer.valueOf(vehicle.getModelYear()), vehicle.getRentalRate(),
                vehicle.getLeaseRate(), vehicle.getStatus());
    }

    /** Inserts a vehicle on a caller-owned connection and returns its generated id. */
    public int insert(java.sql.Connection connection, Vehicle vehicle)
            throws LfrlmsException {
        String sql = "INSERT INTO vehicle (registration_no, brand_id, category_id, branch_id, "
                + "model, model_year, rental_rate, lease_rate, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return executeInsert(connection, sql, vehicle.getRegistrationNo(),
                Integer.valueOf(vehicle.getBrandId()), Integer.valueOf(vehicle.getCategoryId()),
                Integer.valueOf(vehicle.getBranchId()), vehicle.getModel(),
                Integer.valueOf(vehicle.getModelYear()), vehicle.getRentalRate(),
                vehicle.getLeaseRate(), vehicle.getStatus());
    }

    /** Finds a vehicle by id with its stored status, or returns null. */
    public Vehicle findById(int vehicleId) throws LfrlmsException {
        String sql = "SELECT vehicle_id, registration_no, brand_id, category_id, branch_id, "
                + "model, model_year, rental_rate, lease_rate, status, created_at, updated_at "
                + "FROM vehicle WHERE vehicle_id = ?";
        return toVehicle(firstRow(queryRows(sql, Integer.valueOf(vehicleId))));
    }

    /** Returns one sorted page of vehicles with no filters. */
    public ArrayList<Vehicle> findPage(String sortKey, boolean ascending,
            int pageNumber, int pageSize) throws LfrlmsException {
        return search(null, null, null, null, null, null, null, null, null,
                null, null, sortKey, ascending, pageNumber, pageSize);
    }

    /** Searches vehicles by text, lookups, status, rate ranges and availability window. */
    public ArrayList<Vehicle> search(String text, Integer brandId, Integer categoryId,
            Integer branchId, String status, BigDecimal minimumRentalRate,
            BigDecimal maximumRentalRate, BigDecimal minimumLeaseRate,
            BigDecimal maximumLeaseRate, LocalDateTime availableFrom,
            LocalDateTime availableTo, String sortKey, boolean ascending,
            int pageNumber, int pageSize) throws LfrlmsException {
        QueryBuilder query = new QueryBuilder(VEHICLE_SELECT + "WHERE 1 = 1");
        appendVehicleFilters(query, text, brandId, categoryId, branchId, status,
                minimumRentalRate, maximumRentalRate, minimumLeaseRate,
                maximumLeaseRate, availableFrom, availableTo);
        query.appendOrderBy(sortKey, SORT_COLUMNS, "registrationNo", ascending)
                .appendLimitOffset(pageSize, pageNumber);
        ArrayList<HashMap<String, Object>> rows =
                queryRows(query.getSql(), query.getParameters().toArray());
        ArrayList<Vehicle> vehicles = new ArrayList<Vehicle>();
        for (HashMap<String, Object> row : rows) {
            vehicles.add(toVehicle(row));
        }
        return vehicles;
    }

    /** Searches vehicles by text only, sorted by registration number. */
    public ArrayList<Vehicle> search(String text, int pageNumber, int pageSize)
            throws LfrlmsException {
        return search(text, null, null, null, null, null, null, null, null,
                null, null, "registrationNo", true, pageNumber, pageSize);
    }

    /** Counts vehicles that match the search filters. */
    public long countSearchResults(String text, Integer brandId, Integer categoryId,
            Integer branchId, String status, BigDecimal minimumRentalRate,
            BigDecimal maximumRentalRate, BigDecimal minimumLeaseRate,
            BigDecimal maximumLeaseRate, LocalDateTime availableFrom,
            LocalDateTime availableTo) throws LfrlmsException {
        QueryBuilder query = new QueryBuilder("SELECT COUNT(*) AS row_count "
                + "FROM vehicle v JOIN brand b ON b.brand_id = v.brand_id "
                + "JOIN vehicle_category vc ON vc.category_id = v.category_id "
                + "JOIN branch br ON br.branch_id = v.branch_id WHERE 1 = 1");
        appendVehicleFilters(query, text, brandId, categoryId, branchId, status,
                minimumRentalRate, maximumRentalRate, minimumLeaseRate,
                maximumLeaseRate, availableFrom, availableTo);
        return queryCount(query.getSql(), query.getParameters().toArray());
    }

    /** Counts all vehicles. */
    public long countAll() throws LfrlmsException {
        return queryCount("SELECT COUNT(*) AS row_count FROM vehicle");
    }

    /** Lists vehicles free for a period using the sp_available_vehicles procedure. */
    public ArrayList<HashMap<String, Object>> findAvailableVehicles(
            java.time.LocalDateTime from, java.time.LocalDateTime to,
            Integer branchId, Integer categoryId) throws LfrlmsException {
        String call = "{call sp_available_vehicles(?, ?, ?, ?)}";
        return callRows(call, from, to, branchId, categoryId);
    }

    /** Updates a vehicle; returns true if a row was updated. */
    public boolean update(Vehicle vehicle) throws LfrlmsException {
        String sql = "UPDATE vehicle SET registration_no = ?, brand_id = ?, category_id = ?, "
                + "branch_id = ?, model = ?, model_year = ?, rental_rate = ?, lease_rate = ?, "
                + "status = ? WHERE vehicle_id = ?";
        return executeUpdate(sql, vehicle.getRegistrationNo(),
                Integer.valueOf(vehicle.getBrandId()), Integer.valueOf(vehicle.getCategoryId()),
                Integer.valueOf(vehicle.getBranchId()), vehicle.getModel(),
                Integer.valueOf(vehicle.getModelYear()), vehicle.getRentalRate(),
                vehicle.getLeaseRate(), vehicle.getStatus(),
                Integer.valueOf(vehicle.getVehicleId())) > 0;
    }

    /** Updates a vehicle on a caller-owned connection. */
    public boolean update(java.sql.Connection connection, Vehicle vehicle)
            throws LfrlmsException {
        String sql = "UPDATE vehicle SET registration_no = ?, brand_id = ?, category_id = ?, "
                + "branch_id = ?, model = ?, model_year = ?, rental_rate = ?, lease_rate = ?, "
                + "status = ? WHERE vehicle_id = ?";
        return executeUpdate(connection, sql, vehicle.getRegistrationNo(),
                Integer.valueOf(vehicle.getBrandId()), Integer.valueOf(vehicle.getCategoryId()),
                Integer.valueOf(vehicle.getBranchId()), vehicle.getModel(),
                Integer.valueOf(vehicle.getModelYear()), vehicle.getRentalRate(),
                vehicle.getLeaseRate(), vehicle.getStatus(),
                Integer.valueOf(vehicle.getVehicleId())) > 0;
    }

    /** Deletes a vehicle by id; returns true if a row was removed. */
    public boolean delete(int vehicleId) throws LfrlmsException {
        return executeUpdate("DELETE FROM vehicle WHERE vehicle_id = ?",
                Integer.valueOf(vehicleId)) > 0;
    }

    /** Deletes a vehicle on a caller-owned connection. */
    public boolean delete(java.sql.Connection connection, int vehicleId)
            throws LfrlmsException {
        return executeUpdate(connection, "DELETE FROM vehicle WHERE vehicle_id = ?",
                Integer.valueOf(vehicleId)) > 0;
    }

    /** Reads a vehicle and locks its row for the caller's transaction. */
    public Vehicle lockByIdForUpdate(java.sql.Connection connection, int vehicleId)
            throws LfrlmsException {
        // FOR UPDATE locks this row until the caller commits or rolls back
        String sql = "SELECT vehicle_id, registration_no, brand_id, category_id, branch_id, "
                + "model, model_year, rental_rate, lease_rate, status, created_at, updated_at "
                + "FROM vehicle WHERE vehicle_id = ? FOR UPDATE";
        return toVehicle(firstRow(queryRows(connection, sql, Integer.valueOf(vehicleId))));
    }

    private void appendVehicleFilters(QueryBuilder query, String text,
            Integer brandId, Integer categoryId, Integer branchId, String status,
            BigDecimal minimumRentalRate, BigDecimal maximumRentalRate,
            BigDecimal minimumLeaseRate, BigDecimal maximumLeaseRate,
            LocalDateTime availableFrom, LocalDateTime availableTo) {
        query.appendLikeAnyCondition(new String[] {"v.registration_no", "v.model",
                "b.brand_name"}, text);
        appendOptional(query, "v.brand_id", brandId);
        appendOptional(query, "v.category_id", categoryId);
        appendOptional(query, "v.branch_id", branchId);
        if (status != null && !status.trim().isEmpty()) {
            query.appendAndFixedCondition(DISPLAY_STATUS_SQL + " = ?", status.trim());
        }
        appendOptional(query, "v.rental_rate", ">=", minimumRentalRate);
        appendOptional(query, "v.rental_rate", "<=", maximumRentalRate);
        appendOptional(query, "v.lease_rate", ">=", minimumLeaseRate);
        appendOptional(query, "v.lease_rate", "<=", maximumLeaseRate);
        if (availableFrom != null && availableTo != null) {
            appendAvailabilityFilter(query, availableFrom, availableTo);
        }
    }

    private void appendAvailabilityFilter(QueryBuilder query,
            java.time.LocalDateTime from, java.time.LocalDateTime to) {
        String condition = "v.status NOT IN ('Maintenance', 'Inactive') "
                + "AND NOT EXISTS (SELECT 1 FROM rental r WHERE r.vehicle_id = v.vehicle_id "
                + "AND r.status = 'Confirmed' AND r.scheduled_start_datetime < ? "
                + "AND r.expected_return_datetime > ?) "
                + "AND NOT EXISTS (SELECT 1 FROM rental r WHERE r.vehicle_id = v.vehicle_id "
                + "AND r.status = 'Active' AND r.actual_pickup_datetime < ? "
                + "AND (r.expected_return_datetime > ? OR r.expected_return_datetime <= NOW())) "
                + "AND NOT EXISTS (SELECT 1 FROM lease l WHERE l.vehicle_id = v.vehicle_id "
                + "AND l.status IN ('Scheduled', 'Active') "
                + "AND CAST(l.start_date AS DATETIME) < ? "
                + "AND CAST(DATE_ADD(l.end_date, INTERVAL 1 DAY) AS DATETIME) > ?) "
                + "AND NOT EXISTS (SELECT 1 FROM maintenance m "
                + "WHERE m.vehicle_id = v.vehicle_id AND m.status = 'In Progress' "
                + "AND CAST(m.service_date AS DATETIME) < ? )";
        query.appendAndFixedCondition(condition, to, from, to, from, to, from, to);
    }

    private void appendOptional(QueryBuilder query, String column, Integer value) {
        if (value != null) {
            query.appendAndCondition(column, "=", value);
        }
    }

    private void appendOptional(QueryBuilder query, String column, String operator,
            java.math.BigDecimal value) {
        if (value != null) {
            query.appendAndCondition(column, operator, value);
        }
    }

    private Vehicle toVehicle(HashMap<String, Object> row) {
        if (row == null) {
            return null;
        }
        String displayStatus = stringValue(row, "display_status");
        if (displayStatus == null) {
            displayStatus = stringValue(row, "status");
        }
        Vehicle vehicle = new Vehicle(intValue(row, "vehicle_id"),
                stringValue(row, "registration_no"), intValue(row, "brand_id"),
                intValue(row, "category_id"), intValue(row, "branch_id"),
                stringValue(row, "model"), intValue(row, "model_year"),
                decimalValue(row, "rental_rate"), decimalValue(row, "lease_rate"),
                displayStatus);
        vehicle.setCreatedAt(dateTimeValue(row, "created_at"));
        vehicle.setUpdatedAt(dateTimeValue(row, "updated_at"));
        return vehicle;
    }

    private static Map<String, String> createSortColumns() {
        HashMap<String, String> columns = new HashMap<String, String>();
        columns.put("registrationno", "v.registration_no");
        columns.put("brandname", "b.brand_name");
        columns.put("categoryname", "vc.category_name");
        columns.put("branchname", "br.branch_name");
        columns.put("model", "v.model");
        columns.put("year", "v.model_year");
        columns.put("rentalrate", "v.rental_rate");
        columns.put("leaserate", "v.lease_rate");
        columns.put("status", "display_status");
        columns.put("id", "v.vehicle_id");
        return columns;
    }
}
