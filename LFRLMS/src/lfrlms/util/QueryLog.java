package lfrlms.util;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Purpose: Keeps a bounded history of SQL activity for diagnostics.
 * Concept: Queue collections, a private nested class and synchronized methods.
 * Layer: Utility.
 * Rubric: Collections, inner/anonymous classes.
 */
public class QueryLog {
    private static final int MAX_ENTRIES = 200;
    private static final QueryLog SHARED = new QueryLog();
    private final Queue<QueryRecord> records;

    public QueryLog() {
        records = new LinkedList<QueryRecord>();
    }

    public static QueryLog getShared() {
        return SHARED;
    }

    public synchronized void add(String sql, String parameters,
            long elapsedMillis, int rowCount) {
        add(sql, parameters, new Object[0], elapsedMillis, rowCount);
    }

    public synchronized void add(String sql, Object[] parameters,
            long elapsedMillis, int rowCount) {
        Object[] safeParameters = parameters == null ? new Object[0] : parameters.clone();
        add(sql, java.util.Arrays.deepToString(safeParameters), safeParameters,
                elapsedMillis, rowCount);
    }

    private void add(String sql, String parameters, Object[] parameterValues,
            long elapsedMillis, int rowCount) {
        if (records.size() == MAX_ENTRIES) {
            records.remove();
        }
        records.add(new QueryRecord(LocalDateTime.now(), sql, parameters,
                parameterValues, elapsedMillis, rowCount));
    }

    public synchronized ArrayList<String> getRecentEntries() {
        ArrayList<String> result = new ArrayList<String>();
        for (QueryRecord record : records) {
            result.add(record.toDisplayString());
        }
        return result;
    }

    public synchronized ArrayList<QueryEntry> getRecentRecords() {
        ArrayList<QueryEntry> result = new ArrayList<QueryEntry>();
        for (QueryRecord record : records) {
            result.add(new QueryEntry(record.recordedAt, record.sql, record.parameters,
                    record.parameterValues, record.elapsedMillis, record.rowCount));
        }
        Collections.reverse(result);
        return result;
    }

    public synchronized int size() {
        return records.size();
    }

    public synchronized void clear() {
        records.clear();
    }

    /** Purpose: Keeps one immutable SQL activity entry inside the bounded queue. */
    private static class QueryRecord {
        private final LocalDateTime recordedAt;
        private final String sql;
        private final String parameters;
        private final Object[] parameterValues;
        private final long elapsedMillis;
        private final int rowCount;

        private QueryRecord(LocalDateTime recordedAt, String sql, String parameters,
                Object[] parameterValues, long elapsedMillis, int rowCount) {
            this.recordedAt = recordedAt;
            this.sql = sql;
            this.parameters = parameters;
            this.parameterValues = parameterValues.clone();
            this.elapsedMillis = elapsedMillis;
            this.rowCount = rowCount;
        }

        private String toDisplayString() {
            return recordedAt + " | " + sql + " | params=" + parameters
                    + " | " + elapsedMillis + " ms | rows=" + rowCount;
        }
    }

    /** Immutable snapshot used by the diagnostics view and EXPLAIN action. */
    public static final class QueryEntry {
        private final LocalDateTime recordedAt;
        private final String sql;
        private final String parameters;
        private final Object[] parameterValues;
        private final long elapsedMillis;
        private final int rowCount;

        private QueryEntry(LocalDateTime recordedAt, String sql, String parameters,
                Object[] parameterValues, long elapsedMillis, int rowCount) {
            this.recordedAt = recordedAt;
            this.sql = sql;
            this.parameters = parameters;
            this.parameterValues = parameterValues.clone();
            this.elapsedMillis = elapsedMillis;
            this.rowCount = rowCount;
        }

        public LocalDateTime getRecordedAt() {
            return recordedAt;
        }

        public String getSql() {
            return sql;
        }

        public String getParameters() {
            return parameters;
        }

        public Object[] getParameterValues() {
            return parameterValues.clone();
        }

        public long getElapsedMillis() {
            return elapsedMillis;
        }

        public int getRowCount() {
            return rowCount;
        }
    }
}
