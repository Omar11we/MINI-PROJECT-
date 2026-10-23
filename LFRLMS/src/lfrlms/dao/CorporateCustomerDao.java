package lfrlms.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lfrlms.exception.LfrlmsException;
import lfrlms.model.CorporateCustomer;
import lfrlms.util.QueryBuilder;
import lfrlms.util.QueryLog;

/**
 * Purpose: Reads and writes corporate-only customer subtype columns.
 * Concept: JDBC joins, inheritance mapping and prepared statements.
 * Layer: DAO (data access).
 * Rubric: JDBC CRUD, overloading.
 */
public class CorporateCustomerDao extends AbstractDao {
    private static final String SELECT_SQL = "SELECT c.customer_id, c.first_name, c.last_name, "
            + "c.email, c.phone, c.street, c.city, c.pincode, c.created_at, c.updated_at, "
            + "co.company_name, co.gst_no "
            + "FROM corporate_customer co JOIN customer c ON c.customer_id = co.customer_id ";
    private static final Map<String, String> SORT_COLUMNS = createSortColumns();

    public CorporateCustomerDao() {
        super();
    }

    public CorporateCustomerDao(QueryLog queryLog) {
        super(queryLog);
    }

    /** Inserts the corporate subtype row for an existing customer. */
    public boolean insert(CorporateCustomer customer) throws LfrlmsException {
        String sql = "INSERT INTO corporate_customer (customer_id, company_name, gst_no) "
                + "VALUES (?, ?, ?)";
        return executeUpdate(sql, Integer.valueOf(customer.getCustomerId()),
                customer.getCompanyName(), customer.getGstNo()) > 0;
    }

    /** Inserts the corporate subtype row on a caller-owned connection. */
    public boolean insert(java.sql.Connection connection, CorporateCustomer customer)
            throws LfrlmsException {
        String sql = "INSERT INTO corporate_customer (customer_id, company_name, gst_no) "
                + "VALUES (?, ?, ?)";
        return executeUpdate(connection, sql, Integer.valueOf(customer.getCustomerId()),
                customer.getCompanyName(), customer.getGstNo()) > 0;
    }

    /** Finds a corporate customer joined with its parent row, or returns null. */
    public CorporateCustomer findById(int customerId) throws LfrlmsException {
        return toCorporate(firstRow(queryRows(SELECT_SQL + "WHERE co.customer_id = ?",
                Integer.valueOf(customerId))));
    }

    /** Returns one sorted page of corporate customers. */
    public ArrayList<CorporateCustomer> findPage(String sortKey, boolean ascending,
            int pageNumber, int pageSize) throws LfrlmsException {
        QueryBuilder query = new QueryBuilder(SELECT_SQL + "WHERE 1 = 1");
        query.appendOrderBy(sortKey, SORT_COLUMNS, "companyName", ascending)
                .appendLimitOffset(pageSize, pageNumber);
        ArrayList<HashMap<String, Object>> rows =
                queryRows(query.getSql(), query.getParameters().toArray());
        ArrayList<CorporateCustomer> customers = new ArrayList<CorporateCustomer>();
        for (HashMap<String, Object> row : rows) {
            customers.add(toCorporate(row));
        }
        return customers;
    }

    /** Counts corporate customer rows. */
    public long countAll() throws LfrlmsException {
        return queryCount("SELECT COUNT(*) AS row_count FROM corporate_customer");
    }

    /** Updates the company name and GST number. */
    public boolean update(CorporateCustomer customer) throws LfrlmsException {
        String sql = "UPDATE corporate_customer SET company_name = ?, gst_no = ? "
                + "WHERE customer_id = ?";
        return executeUpdate(sql, customer.getCompanyName(), customer.getGstNo(),
                Integer.valueOf(customer.getCustomerId())) > 0;
    }

    /** Updates the company name and GST number on a caller-owned connection. */
    public boolean update(java.sql.Connection connection, CorporateCustomer customer)
            throws LfrlmsException {
        String sql = "UPDATE corporate_customer SET company_name = ?, gst_no = ? "
                + "WHERE customer_id = ?";
        return executeUpdate(connection, sql, customer.getCompanyName(), customer.getGstNo(),
                Integer.valueOf(customer.getCustomerId())) > 0;
    }

    /** Deletes the corporate subtype row for a customer. */
    public boolean delete(int customerId) throws LfrlmsException {
        return executeUpdate("DELETE FROM corporate_customer WHERE customer_id = ?",
                Integer.valueOf(customerId)) > 0;
    }

    /** Deletes the corporate subtype row on a caller-owned connection. */
    public boolean delete(java.sql.Connection connection, int customerId)
            throws LfrlmsException {
        return executeUpdate(connection,
                "DELETE FROM corporate_customer WHERE customer_id = ?",
                Integer.valueOf(customerId)) > 0;
    }

    private CorporateCustomer toCorporate(HashMap<String, Object> row) {
        if (row == null) {
            return null;
        }
        CorporateCustomer customer = new CorporateCustomer(intValue(row, "customer_id"),
                stringValue(row, "first_name"), stringValue(row, "last_name"),
                stringValue(row, "email"), stringValue(row, "phone"),
                stringValue(row, "street"), stringValue(row, "city"),
                stringValue(row, "pincode"), stringValue(row, "company_name"),
                stringValue(row, "gst_no"));
        customer.setCreatedAt(dateTimeValue(row, "created_at"));
        customer.setUpdatedAt(dateTimeValue(row, "updated_at"));
        return customer;
    }

    private static Map<String, String> createSortColumns() {
        HashMap<String, String> columns = new HashMap<String, String>();
        columns.put("companyname", "co.company_name");
        columns.put("lastname", "c.last_name, c.first_name");
        columns.put("id", "c.customer_id");
        return columns;
    }
}
