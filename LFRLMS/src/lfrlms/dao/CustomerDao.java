package lfrlms.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lfrlms.exception.LfrlmsException;
import lfrlms.model.CorporateCustomer;
import lfrlms.model.Customer;
import lfrlms.model.IndividualCustomer;
import lfrlms.util.Constants;
import lfrlms.util.QueryBuilder;
import lfrlms.util.QueryLog;

/**
 * Purpose: Reads parent customer rows with their optional subtype data.
 * Concept: JDBC joins, polymorphism and server-side pagination.
 * Layer: DAO (data access).
 * Rubric: JDBC CRUD, overloading.
 */
public class CustomerDao extends AbstractDao {
    private static final String CUSTOMER_SELECT = "SELECT c.customer_id, c.customer_type, "
            + "c.first_name, c.last_name, c.email, c.phone, c.street, c.city, c.pincode, "
            + "c.created_at, c.updated_at, i.date_of_birth AS individual_date_of_birth, "
            + "i.license_no AS individual_license_no, "
            + "i.license_expiry AS individual_license_expiry, "
            + "co.company_name AS corporate_company_name, co.gst_no AS corporate_gst_no "
            + "FROM customer c LEFT JOIN individual_customer i ON i.customer_id = c.customer_id "
            + "LEFT JOIN corporate_customer co ON co.customer_id = c.customer_id ";
    private static final Map<String, String> SORT_COLUMNS = createSortColumns();

    public CustomerDao() {
        super();
    }

    public CustomerDao(QueryLog queryLog) {
        super(queryLog);
    }

    /** Inserts a parent customer row and returns its generated id. */
    public int insert(Customer customer) throws LfrlmsException {
        String sql = "INSERT INTO customer (customer_type, first_name, last_name, email, "
                + "phone, street, city, pincode) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        return executeInsert(sql, customer.getCustomerType(), customer.getFirstName(),
                customer.getLastName(), customer.getEmail(), customer.getPhone(),
                customer.getStreet(), customer.getCity(), customer.getPincode());
    }

    /** Inserts a parent customer row on a caller-owned connection. */
    public int insert(java.sql.Connection connection, Customer customer)
            throws LfrlmsException {
        String sql = "INSERT INTO customer (customer_type, first_name, last_name, email, "
                + "phone, street, city, pincode) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        return executeInsert(connection, sql, customer.getCustomerType(),
                customer.getFirstName(), customer.getLastName(), customer.getEmail(),
                customer.getPhone(), customer.getStreet(), customer.getCity(),
                customer.getPincode());
    }

    /** Finds a customer and builds the matching subclass, or returns null. */
    public Customer findById(int customerId) throws LfrlmsException {
        String sql = CUSTOMER_SELECT + "WHERE c.customer_id = ?";
        return toCustomer(firstRow(queryRows(sql, Integer.valueOf(customerId))));
    }

    /** Returns one sorted page of customers with no filters. */
    public ArrayList<Customer> findPage(String sortKey, boolean ascending,
            int pageNumber, int pageSize) throws LfrlmsException {
        return search(null, null, null, sortKey, ascending, pageNumber, pageSize);
    }

    /** Searches customers by text, type and licence expiry, one page at a time. */
    public ArrayList<Customer> search(String text, String customerType,
            Integer licenseExpiryWithinDays, String sortKey, boolean ascending,
            int pageNumber, int pageSize) throws LfrlmsException {
        QueryBuilder query = new QueryBuilder(CUSTOMER_SELECT + "WHERE 1 = 1");
        appendCustomerFilters(query, text, customerType, licenseExpiryWithinDays);
        query.appendOrderBy(sortKey, SORT_COLUMNS, "lastName", ascending)
                .appendLimitOffset(pageSize, pageNumber);
        ArrayList<HashMap<String, Object>> rows =
                queryRows(query.getSql(), query.getParameters().toArray());
        ArrayList<Customer> customers = new ArrayList<Customer>();
        for (HashMap<String, Object> row : rows) {
            customers.add(toCustomer(row));
        }
        return customers;
    }

    /** Counts customers that match the search filters. */
    public long countSearchResults(String text, String customerType,
            Integer licenseExpiryWithinDays) throws LfrlmsException {
        String fromClause = "FROM customer c "
                + "LEFT JOIN individual_customer i ON i.customer_id = c.customer_id "
                + "LEFT JOIN corporate_customer co ON co.customer_id = c.customer_id ";
        QueryBuilder query = new QueryBuilder("SELECT COUNT(*) AS row_count "
                + fromClause + "WHERE 1 = 1");
        appendCustomerFilters(query, text, customerType, licenseExpiryWithinDays);
        return queryCount(query.getSql(), query.getParameters().toArray());
    }

    /** Counts all customers. */
    public long countAll() throws LfrlmsException {
        return queryCount("SELECT COUNT(*) AS row_count FROM customer");
    }

    /** Checks whether the customer has any rental or lease. */
    public boolean hasTransactionHistory(int customerId) throws LfrlmsException {
        String sql = "SELECT COUNT(*) AS transaction_count FROM ("
                + "SELECT rental_id FROM rental WHERE customer_id = ? "
                + "UNION ALL SELECT lease_id FROM lease WHERE customer_id = ?"
                + ") AS customer_transactions";
        return queryCount(sql, Integer.valueOf(customerId), Integer.valueOf(customerId)) > 0;
    }

    /** Checks transaction history on a caller-owned transaction connection. */
    public boolean hasTransactionHistory(java.sql.Connection connection, int customerId)
            throws LfrlmsException {
        String sql = "SELECT COUNT(*) AS transaction_count FROM ("
                + "SELECT rental_id FROM rental WHERE customer_id = ? "
                + "UNION ALL SELECT lease_id FROM lease WHERE customer_id = ?"
                + ") AS customer_transactions";
        return queryCount(connection, sql, Integer.valueOf(customerId),
                Integer.valueOf(customerId)) > 0;
    }

    /** Updates the parent customer row. */
    public boolean update(Customer customer) throws LfrlmsException {
        String sql = "UPDATE customer SET customer_type = ?, first_name = ?, last_name = ?, "
                + "email = ?, phone = ?, street = ?, city = ?, pincode = ? "
                + "WHERE customer_id = ?";
        return executeUpdate(sql, customer.getCustomerType(), customer.getFirstName(),
                customer.getLastName(), customer.getEmail(), customer.getPhone(),
                customer.getStreet(), customer.getCity(), customer.getPincode(),
                Integer.valueOf(customer.getCustomerId())) > 0;
    }

    /** Updates the parent customer row on a caller-owned connection. */
    public boolean update(java.sql.Connection connection, Customer customer)
            throws LfrlmsException {
        String sql = "UPDATE customer SET customer_type = ?, first_name = ?, last_name = ?, "
                + "email = ?, phone = ?, street = ?, city = ?, pincode = ? "
                + "WHERE customer_id = ?";
        return executeUpdate(connection, sql, customer.getCustomerType(),
                customer.getFirstName(), customer.getLastName(), customer.getEmail(),
                customer.getPhone(), customer.getStreet(), customer.getCity(),
                customer.getPincode(), Integer.valueOf(customer.getCustomerId())) > 0;
    }

    /** Deletes the parent customer row. */
    public boolean delete(int customerId) throws LfrlmsException {
        return executeUpdate("DELETE FROM customer WHERE customer_id = ?",
                Integer.valueOf(customerId)) > 0;
    }

    /** Deletes the parent customer row on a caller-owned connection. */
    public boolean delete(java.sql.Connection connection, int customerId)
            throws LfrlmsException {
        return executeUpdate(connection, "DELETE FROM customer WHERE customer_id = ?",
                Integer.valueOf(customerId)) > 0;
    }

    private void appendCustomerFilters(QueryBuilder query, String text,
            String customerType, Integer licenseExpiryWithinDays) {
        query.appendLikeAnyCondition(new String[] {"c.first_name", "c.last_name", "c.email",
                "c.phone", "co.company_name", "i.license_no"}, text);
        if (customerType != null && !customerType.trim().isEmpty()) {
            query.appendAndCondition("c.customer_type", "=", customerType.trim());
        }
        if (licenseExpiryWithinDays != null) {
            query.appendAndFixedCondition("i.license_expiry >= CURDATE() "
                    + "AND i.license_expiry <= DATE_ADD(CURDATE(), INTERVAL ? DAY)",
                    licenseExpiryWithinDays);
        }
    }

    private Customer toCustomer(HashMap<String, Object> row) {
        if (row == null) {
            return null;
        }
        int id = intValue(row, "customer_id");
        String firstName = stringValue(row, "first_name");
        String lastName = stringValue(row, "last_name");
        String email = stringValue(row, "email");
        String phone = stringValue(row, "phone");
        String street = stringValue(row, "street");
        String city = stringValue(row, "city");
        String pincode = stringValue(row, "pincode");
        Customer customer;
        if (Constants.CUSTOMER_INDIVIDUAL.equals(stringValue(row, "customer_type"))) {
            customer = new IndividualCustomer(id, firstName, lastName, email, phone,
                    street, city, pincode, dateValue(row, "individual_date_of_birth"),
                    stringValue(row, "individual_license_no"),
                    dateValue(row, "individual_license_expiry"));
        } else {
            customer = new CorporateCustomer(id, firstName, lastName, email, phone,
                    street, city, pincode, stringValue(row, "corporate_company_name"),
                    stringValue(row, "corporate_gst_no"));
        }
        customer.setCreatedAt(dateTimeValue(row, "created_at"));
        customer.setUpdatedAt(dateTimeValue(row, "updated_at"));
        return customer;
    }

    private static Map<String, String> createSortColumns() {
        HashMap<String, String> columns = new HashMap<String, String>();
        columns.put("lastname", "c.last_name, c.first_name");
        columns.put("firstname", "c.first_name, c.last_name");
        columns.put("email", "c.email");
        columns.put("id", "c.customer_id");
        return columns;
    }
}
