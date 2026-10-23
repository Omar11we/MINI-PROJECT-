package lfrlms.dao;

import java.util.ArrayList;
import java.util.HashMap;
import lfrlms.exception.LfrlmsException;
import lfrlms.util.QueryLog;

/**
 * Purpose: Reads database counts and current-month revenue for the dashboard.
 * Concept: JDBC aggregate queries and collections.
 * Layer: DAO (data access).
 * Rubric: JDBC CRUD, Collections.
 */
public class DashboardDao extends AbstractDao {
    public DashboardDao() {
        super();
    }

    public DashboardDao(QueryLog queryLog) {
        super(queryLog);
    }

    /** Reads fleet counts, active contracts and this month's revenue in one query. */
    public HashMap<String, Object> getSummary() throws LfrlmsException {
        String sql = "SELECT "
                + "(SELECT COUNT(*) FROM vehicle) AS total_vehicles, "
                + "(SELECT COUNT(*) FROM vehicle WHERE status = 'Available') AS available_vehicles, "
                + "(SELECT COUNT(*) FROM vehicle WHERE status = 'Rented') AS rented_vehicles, "
                + "(SELECT COUNT(*) FROM vehicle WHERE status = 'Leased') AS leased_vehicles, "
                + "(SELECT COUNT(*) FROM vehicle WHERE status = 'Maintenance') AS maintenance_vehicles, "
                + "(SELECT COUNT(*) FROM rental WHERE status = 'Active') AS active_rentals, "
                + "(SELECT COUNT(*) FROM lease WHERE status = 'Active') AS active_leases, "
                + "(SELECT COUNT(*) FROM rental WHERE status = 'Confirmed' "
                + "AND scheduled_start_datetime >= NOW() "
                + "AND scheduled_start_datetime < DATE_ADD(NOW(), INTERVAL 24 HOUR)) "
                + "AS upcoming_rentals, "
                + "(SELECT COALESCE(SUM(amount), 0.00) FROM payment "
                + "WHERE status = ? AND payment_date >= DATE_FORMAT(NOW(), '%Y-%m-01')) "
                + "AS monthly_revenue";
        ArrayList<HashMap<String, Object>> rows = queryRows(sql, "Success");
        return firstRow(rows);
    }
}
