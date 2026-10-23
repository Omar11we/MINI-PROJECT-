package lfrlms.dao;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lfrlms.exception.LfrlmsException;
import lfrlms.model.Maintenance;
import lfrlms.util.QueryBuilder;
import lfrlms.util.QueryLog;

/**
 * Purpose: Reads and writes maintenance rows without managing vehicle status.
 * Concept: JDBC CRUD, nullable dates and server-side pagination.
 * Layer: DAO (data access).
 * Rubric: JDBC CRUD, overloading.
 */
public class MaintenanceDao extends AbstractDao {
    private static final String MAINTENANCE_COLUMNS = "m.maintenance_id, m.vehicle_id, "
            + "m.service_date, m.service_type, m.description, m.cost, "
            + "m.next_service_date, m.completed_date, m.status, m.created_at, m.updated_at ";
    private static final String SEARCH_FROM = "FROM maintenance m "
            + "JOIN vehicle v ON v.vehicle_id = m.vehicle_id ";
    private static final Map<String, String> SORT_COLUMNS = createSortColumns();

    public MaintenanceDao() {
        super();
    }

    public MaintenanceDao(QueryLog queryLog) {
        super(queryLog);
    }

    /** Inserts a maintenance record and returns its generated id. */
    public int insert(Maintenance maintenance) throws LfrlmsException {
        String sql = "INSERT INTO maintenance (vehicle_id, service_date, service_type, "
                + "description, cost, next_service_date, completed_date, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        return executeInsert(sql, Integer.valueOf(maintenance.getVehicleId()),
                maintenance.getServiceDate(), maintenance.getServiceType(),
                maintenance.getDescription(), maintenance.getCost(),
                maintenance.getNextServiceDate(), maintenance.getCompletedDate(),
                maintenance.getStatus());
    }

    /** Inserts a maintenance record on a caller-owned connection and returns its generated id. */
    public int insert(java.sql.Connection connection, Maintenance maintenance)
            throws LfrlmsException {
        String sql = "INSERT INTO maintenance (vehicle_id, service_date, service_type, "
                + "description, cost, next_service_date, completed_date, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        return executeInsert(connection, sql, Integer.valueOf(maintenance.getVehicleId()),
                maintenance.getServiceDate(), maintenance.getServiceType(),
                maintenance.getDescription(), maintenance.getCost(),
                maintenance.getNextServiceDate(), maintenance.getCompletedDate(),
                maintenance.getStatus());
    }

    /** Finds a maintenance record by id, or returns null. */
    public Maintenance findById(int maintenanceId) throws LfrlmsException {
        String sql = "SELECT " + MAINTENANCE_COLUMNS + "FROM maintenance m "
                + "WHERE m.maintenance_id = ?";
        return toMaintenance(firstRow(queryRows(sql, Integer.valueOf(maintenanceId))));
    }

    /** Returns one sorted page of maintenance records with no filters. */
    public ArrayList<Maintenance> findPage(String sortKey, boolean ascending,
            int pageNumber, int pageSize) throws LfrlmsException {
        return search(null, null, null, null, sortKey, ascending, pageNumber, pageSize);
    }

    /** Searches maintenance records by text, status and service-date range. */
    public ArrayList<Maintenance> search(String text, String status,
            LocalDate serviceFrom, LocalDate serviceTo, String sortKey,
            boolean ascending, int pageNumber, int pageSize) throws LfrlmsException {
        QueryBuilder query = new QueryBuilder("SELECT " + MAINTENANCE_COLUMNS
                + SEARCH_FROM + "WHERE 1 = 1");
        appendFilters(query, text, status, serviceFrom, serviceTo);
        query.appendOrderBy(sortKey, SORT_COLUMNS, "serviceDate", ascending)
                .appendLimitOffset(pageSize, pageNumber);
        ArrayList<HashMap<String, Object>> rows =
                queryRows(query.getSql(), query.getParameters().toArray());
        ArrayList<Maintenance> records = new ArrayList<Maintenance>();
        for (HashMap<String, Object> row : rows) {
            records.add(toMaintenance(row));
        }
        return records;
    }

    /** Counts maintenance records that match the search filters. */
    public long countSearchResults(String text, String status,
            LocalDate serviceFrom, LocalDate serviceTo) throws LfrlmsException {
        QueryBuilder query = new QueryBuilder("SELECT COUNT(*) AS row_count "
                + SEARCH_FROM + "WHERE 1 = 1");
        appendFilters(query, text, status, serviceFrom, serviceTo);
        return queryCount(query.getSql(), query.getParameters().toArray());
    }

    /** Counts all maintenance records. */
    public long countAll() throws LfrlmsException {
        return queryCount("SELECT COUNT(*) AS row_count FROM maintenance");
    }

    /** Updates a maintenance record; returns true if a row was updated. */
    public boolean update(Maintenance maintenance) throws LfrlmsException {
        String sql = "UPDATE maintenance SET vehicle_id = ?, service_date = ?, "
                + "service_type = ?, description = ?, cost = ?, next_service_date = ?, "
                + "completed_date = ?, status = ? WHERE maintenance_id = ?";
        return executeUpdate(sql, Integer.valueOf(maintenance.getVehicleId()),
                maintenance.getServiceDate(), maintenance.getServiceType(),
                maintenance.getDescription(), maintenance.getCost(),
                maintenance.getNextServiceDate(), maintenance.getCompletedDate(),
                maintenance.getStatus(), Integer.valueOf(maintenance.getMaintenanceId())) > 0;
    }

    /** Updates a maintenance record on a caller-owned connection. */
    public boolean update(java.sql.Connection connection, Maintenance maintenance)
            throws LfrlmsException {
        String sql = "UPDATE maintenance SET vehicle_id = ?, service_date = ?, "
                + "service_type = ?, description = ?, cost = ?, next_service_date = ?, "
                + "completed_date = ?, status = ? WHERE maintenance_id = ?";
        return executeUpdate(connection, sql, Integer.valueOf(maintenance.getVehicleId()),
                maintenance.getServiceDate(), maintenance.getServiceType(),
                maintenance.getDescription(), maintenance.getCost(),
                maintenance.getNextServiceDate(), maintenance.getCompletedDate(),
                maintenance.getStatus(), Integer.valueOf(maintenance.getMaintenanceId())) > 0;
    }

    /** Deletes a maintenance record by id; returns true if a row was removed. */
    public boolean delete(int maintenanceId) throws LfrlmsException {
        return executeUpdate("DELETE FROM maintenance WHERE maintenance_id = ?",
                Integer.valueOf(maintenanceId)) > 0;
    }

    /** Deletes a maintenance record on a caller-owned connection. */
    public boolean delete(java.sql.Connection connection, int maintenanceId)
            throws LfrlmsException {
        return executeUpdate(connection,
                "DELETE FROM maintenance WHERE maintenance_id = ?",
                Integer.valueOf(maintenanceId)) > 0;
    }

    private void appendFilters(QueryBuilder query, String text, String status,
            LocalDate serviceFrom, LocalDate serviceTo) {
        query.appendLikeAnyCondition(new String[] {"m.service_type", "m.description",
                "v.registration_no", "v.model"}, text);
        if (status != null && !status.trim().isEmpty()) {
            query.appendAndCondition("m.status", "=", status.trim());
        }
        if (serviceFrom != null) {
            query.appendAndCondition("m.service_date", ">=", serviceFrom);
        }
        if (serviceTo != null) {
            query.appendAndCondition("m.service_date", "<=", serviceTo);
        }
    }

    private Maintenance toMaintenance(HashMap<String, Object> row) {
        if (row == null) {
            return null;
        }
        Maintenance record = new Maintenance(intValue(row, "maintenance_id"),
                intValue(row, "vehicle_id"), dateValue(row, "service_date"),
                stringValue(row, "service_type"), stringValue(row, "description"),
                decimalValue(row, "cost"), dateValue(row, "next_service_date"),
                dateValue(row, "completed_date"), stringValue(row, "status"));
        record.setCreatedAt(dateTimeValue(row, "created_at"));
        record.setUpdatedAt(dateTimeValue(row, "updated_at"));
        return record;
    }

    private static Map<String, String> createSortColumns() {
        HashMap<String, String> columns = new HashMap<String, String>();
        columns.put("servicedate", "m.service_date");
        columns.put("status", "m.status");
        columns.put("servicetype", "m.service_type");
        columns.put("id", "m.maintenance_id");
        return columns;
    }
}
