package lfrlms.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lfrlms.exception.LfrlmsException;
import lfrlms.model.Brand;
import lfrlms.util.QueryBuilder;
import lfrlms.util.QueryLog;

/**
 * Purpose: Reads and writes brand rows with bound SQL parameters.
 * Concept: JDBC CRUD and server-side pagination.
 * Layer: DAO (data access).
 * Rubric: JDBC CRUD, Inheritance.
 */
public class BrandDao extends AbstractDao {
    private static final Map<String, String> SORT_COLUMNS = createSortColumns();

    public BrandDao() {
        super();
    }

    public BrandDao(QueryLog queryLog) {
        super(queryLog);
    }

    /** Inserts a brand and returns its generated id. */
    public int insert(Brand brand) throws LfrlmsException {
        String sql = "INSERT INTO brand (brand_name) VALUES (?)";
        return executeInsert(sql, brand.getBrandName());
    }

    /** Finds a brand by id, or returns null. */
    public Brand findById(int brandId) throws LfrlmsException {
        String sql = "SELECT brand_id, brand_name FROM brand WHERE brand_id = ?";
        return toBrand(firstRow(queryRows(sql, Integer.valueOf(brandId))));
    }

    /** Returns one sorted page of brands. */
    public ArrayList<Brand> findPage(String sortKey, boolean ascending,
            int pageNumber, int pageSize) throws LfrlmsException {
        QueryBuilder query = new QueryBuilder(
                "SELECT brand_id, brand_name FROM brand WHERE 1 = 1");
        query.appendOrderBy(sortKey, SORT_COLUMNS, "brandName", ascending)
                .appendLimitOffset(pageSize, pageNumber);
        ArrayList<HashMap<String, Object>> rows =
                queryRows(query.getSql(), query.getParameters().toArray());
        ArrayList<Brand> brands = new ArrayList<Brand>();
        for (HashMap<String, Object> row : rows) {
            brands.add(toBrand(row));
        }
        return brands;
    }

    /** Counts all brands. */
    public long countAll() throws LfrlmsException {
        return queryCount("SELECT COUNT(*) AS row_count FROM brand");
    }

    /** Updates a brand; returns true if a row was updated. */
    public boolean update(Brand brand) throws LfrlmsException {
        String sql = "UPDATE brand SET brand_name = ? WHERE brand_id = ?";
        return executeUpdate(sql, brand.getBrandName(),
                Integer.valueOf(brand.getBrandId())) > 0;
    }

    /** Deletes a brand by id; returns true if a row was removed. */
    public boolean delete(int brandId) throws LfrlmsException {
        return executeUpdate("DELETE FROM brand WHERE brand_id = ?",
                Integer.valueOf(brandId)) > 0;
    }

    private Brand toBrand(HashMap<String, Object> row) {
        if (row == null) {
            return null;
        }
        return new Brand(intValue(row, "brand_id"), stringValue(row, "brand_name"));
    }

    private static Map<String, String> createSortColumns() {
        HashMap<String, String> columns = new HashMap<String, String>();
        columns.put("brandname", "brand_name");
        columns.put("id", "brand_id");
        return columns;
    }
}
