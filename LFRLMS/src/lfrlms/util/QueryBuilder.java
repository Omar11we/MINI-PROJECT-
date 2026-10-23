package lfrlms.util;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

/**
 * Purpose: Builds paginated SQL from trusted fragments and bound values.
 * Concept: StringBuffer, collections and prepared-statement parameters.
 * Layer: Utility.
 * Rubric: StringBuffer.
 */
public final class QueryBuilder {
    private final StringBuffer sql;
    private final ArrayList<Object> parameters;

    public QueryBuilder(String fixedBaseSql) {
        if (fixedBaseSql == null || fixedBaseSql.trim().isEmpty()) {
            throw new IllegalArgumentException("A fixed SQL starting fragment is required.");
        }
        sql = new StringBuffer(fixedBaseSql);
        parameters = new ArrayList<Object>();
    }

    public QueryBuilder appendAndCondition(String fixedColumn, String operator,
            Object value) {
        if (!isColumnName(fixedColumn) || !isAllowedOperator(operator)) {
            throw new IllegalArgumentException("Use a fixed column and supported operator.");
        }
        sql.append(" AND ").append(fixedColumn).append(' ').append(operator).append(" ?");
        parameters.add(value);
        return this;
    }

    public QueryBuilder appendAndFixedCondition(String fixedCondition,
            Object... values) {
        if (fixedCondition == null || countPlaceholders(fixedCondition) != values.length) {
            throw new IllegalArgumentException(
                    "A fixed condition must have one placeholder per value.");
        }
        sql.append(" AND (").append(fixedCondition).append(')');
        addValues(values);
        return this;
    }

    public QueryBuilder appendLikeAnyCondition(String[] fixedColumns, String text) {
        if (fixedColumns == null || fixedColumns.length == 0
                || text == null || text.trim().isEmpty()) {
            return this;
        }
        sql.append(" AND (");
        String escapedText = "%" + escapeLikeWildcards(text.trim()) + "%";
        for (int index = 0; index < fixedColumns.length; index++) {
            if (!isColumnName(fixedColumns[index])) {
                throw new IllegalArgumentException("LIKE columns must be fixed identifiers.");
            }
            if (index > 0) {
                sql.append(" OR ");
            }
            sql.append(fixedColumns[index]).append(" LIKE ? ESCAPE '!'");
            parameters.add(escapedText);
        }
        sql.append(')');
        return this;
    }

    public QueryBuilder appendOrderBy(String requestedKey,
            Map<String, String> allowedColumns, String defaultKey, boolean ascending) {
        if (allowedColumns == null || allowedColumns.isEmpty()) {
            return this;
        }
        String key = requestedKey == null ? "" : requestedKey.trim().toLowerCase(Locale.ROOT);
        String column = allowedColumns.get(key);
        if (column == null && defaultKey != null) {
            column = allowedColumns.get(defaultKey.trim().toLowerCase(Locale.ROOT));
        }
        if (column == null) {
            return this;
        }
        sql.append(" ORDER BY ").append(column).append(ascending ? " ASC" : " DESC");
        return this;
    }

    public QueryBuilder appendLimitOffset(int pageSize, int pageNumber) {
        int safePageSize = pageSize < 1 ? 1 : pageSize;
        int safePageNumber = pageNumber < 1 ? 1 : pageNumber;
        long offset = ((long) safePageNumber - 1L) * safePageSize;
        sql.append(" LIMIT ? OFFSET ?");
        parameters.add(Integer.valueOf(safePageSize));
        parameters.add(Long.valueOf(offset));
        return this;
    }

    public String getSql() {
        return sql.toString();
    }

    public ArrayList<Object> getParameters() {
        return new ArrayList<Object>(parameters);
    }

    public static String escapeLikeWildcards(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private void addValues(Object[] values) {
        for (Object value : values) {
            parameters.add(value);
        }
    }

    private int countPlaceholders(String fragment) {
        int count = 0;
        for (int index = 0; index < fragment.length(); index++) {
            if (fragment.charAt(index) == '?') {
                count++;
            }
        }
        return count;
    }

    private boolean isColumnName(String column) {
        if (column == null || column.isEmpty()) {
            return false;
        }
        boolean needsName = true;
        for (int index = 0; index < column.length(); index++) {
            char character = column.charAt(index);
            if (character == '.') {
                if (needsName) {
                    return false;
                }
                needsName = true;
            } else if (Character.isLetterOrDigit(character) || character == '_') {
                if (needsName && Character.isDigit(character)) {
                    return false;
                }
                needsName = false;
            } else {
                return false;
            }
        }
        return !needsName;
    }

    private boolean isAllowedOperator(String operator) {
        return "=".equals(operator) || "<>".equals(operator)
                || ">".equals(operator) || ">=".equals(operator)
                || "<".equals(operator) || "<=".equals(operator);
    }
}
