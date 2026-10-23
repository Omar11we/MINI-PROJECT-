package lfrlms.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lfrlms.exception.LfrlmsException;
import lfrlms.model.Branch;
import lfrlms.util.QueryBuilder;
import lfrlms.util.QueryLog;

/**
 * Purpose: Reads and writes branch lookup rows.
 * Concept: JDBC CRUD, collections and server-side pagination.
 * Layer: DAO (data access).
 * Rubric: JDBC CRUD, Inheritance.
 */
public class BranchDao extends AbstractDao {
    private static final Map<String, String> SORT_COLUMNS = createSortColumns();

    public BranchDao() {
        super();
    }

    public BranchDao(QueryLog queryLog) {
        super(queryLog);
    }

    /** Inserts a branch and returns its generated id. */
    public int insert(Branch branch) throws LfrlmsException {
        String sql = "INSERT INTO branch (branch_name, street, city, pincode, phone) "
                + "VALUES (?, ?, ?, ?, ?)";
        return executeInsert(sql, branch.getBranchName(), branch.getStreet(),
                branch.getCity(), branch.getPincode(), branch.getPhone());
    }

    /** Finds a branch by id, or returns null. */
    public Branch findById(int branchId) throws LfrlmsException {
        String sql = "SELECT branch_id, branch_name, street, city, pincode, phone "
                + "FROM branch WHERE branch_id = ?";
        return toBranch(firstRow(queryRows(sql, Integer.valueOf(branchId))));
    }

    /** Returns one sorted page of branches. */
    public ArrayList<Branch> findPage(String sortKey, boolean ascending,
            int pageNumber, int pageSize) throws LfrlmsException {
        QueryBuilder query = new QueryBuilder("SELECT branch_id, branch_name, street, city, "
                + "pincode, phone FROM branch WHERE 1 = 1");
        query.appendOrderBy(sortKey, SORT_COLUMNS, "branchName", ascending)
                .appendLimitOffset(pageSize, pageNumber);
        ArrayList<HashMap<String, Object>> rows =
                queryRows(query.getSql(), query.getParameters().toArray());
        ArrayList<Branch> branches = new ArrayList<Branch>();
        for (HashMap<String, Object> row : rows) {
            branches.add(toBranch(row));
        }
        return branches;
    }

    /** Counts all branches. */
    public long countAll() throws LfrlmsException {
        return queryCount("SELECT COUNT(*) AS row_count FROM branch");
    }

    /** Updates a branch; returns true if a row was updated. */
    public boolean update(Branch branch) throws LfrlmsException {
        String sql = "UPDATE branch SET branch_name = ?, street = ?, city = ?, "
                + "pincode = ?, phone = ? WHERE branch_id = ?";
        return executeUpdate(sql, branch.getBranchName(), branch.getStreet(), branch.getCity(),
                branch.getPincode(), branch.getPhone(), Integer.valueOf(branch.getBranchId())) > 0;
    }

    /** Deletes a branch by id; returns true if a row was removed. */
    public boolean delete(int branchId) throws LfrlmsException {
        return executeUpdate("DELETE FROM branch WHERE branch_id = ?",
                Integer.valueOf(branchId)) > 0;
    }

    private Branch toBranch(HashMap<String, Object> row) {
        if (row == null) {
            return null;
        }
        return new Branch(intValue(row, "branch_id"), stringValue(row, "branch_name"),
                stringValue(row, "street"), stringValue(row, "city"),
                stringValue(row, "pincode"), stringValue(row, "phone"));
    }

    private static Map<String, String> createSortColumns() {
        HashMap<String, String> columns = new HashMap<String, String>();
        columns.put("branchname", "branch_name");
        columns.put("city", "city");
        columns.put("id", "branch_id");
        return columns;
    }
}
