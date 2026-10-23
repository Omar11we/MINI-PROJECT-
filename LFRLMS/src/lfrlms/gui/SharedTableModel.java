package lfrlms.gui;

import java.awt.Color;
import java.awt.Component;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Vector;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

/**
 * Purpose: Supplies non-editable, replaceable rows to module tables.
 * Concept: Vector collections and Swing table models.
 * Layer: GUI (presentation).
 * Rubric: GUI, Collections.
 */
public class SharedTableModel extends DefaultTableModel {
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private JTable attachedTable;

    public SharedTableModel(String[] columns) {
        super(columns, 0);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    public void replaceRows(Vector<Vector<Object>> rows) {
        Vector<String> identifiers = new Vector<String>();
        for (int column = 0; column < getColumnCount(); column++) {
            identifiers.add(getColumnName(column));
        }
        setDataVector(rows, identifiers);
        if (attachedTable != null) {
            applyColumnRenderers(attachedTable);
        }
    }

    public static JTable createTable(SharedTableModel model) {
        JTable table = new JTable(model);
        table.setFont(Theme.BASE_FONT);
        table.setRowHeight(Theme.ROW_HEIGHT);
        table.setBackground(Theme.TABLE_BACKGROUND);
        table.setForeground(Theme.TEXT_COLOR);
        table.setSelectionBackground(Theme.ACCENT_COLOR);
        table.setSelectionForeground(Color.WHITE);
        table.setGridColor(new Color(221, 228, 234));
        JTableHeader header = table.getTableHeader();
        header.setFont(Theme.BASE_FONT.deriveFont(java.awt.Font.BOLD));
        header.setBackground(Theme.HEADER_COLOR);
        header.setForeground(Color.WHITE);
        model.attachedTable = table;
        model.applyColumnRenderers(table);
        return table;
    }

    private void applyColumnRenderers(JTable table) {
        for (int column = 0; column < getColumnCount(); column++) {
            String name = getColumnName(column).toLowerCase();
            if (isDateTimeColumn(name, column)) {
                table.getColumnModel().getColumn(column).setPreferredWidth(170);
            }
            if (name.contains("status")) {
                table.getColumnModel().getColumn(column)
                        .setCellRenderer(new StatusColourRenderer());
            } else {
                table.getColumnModel().getColumn(column)
                        .setCellRenderer(new ZebraDateTimeRenderer());
            }
        }
    }

    private boolean isDateTimeColumn(String name, int column) {
        if (name.contains("datetime") || name.contains("date/time")
                || name.contains("start") || name.contains("return")
                || name.contains("pickup") || name.contains("time")) {
            return true;
        }
        if (name.equals("payment date")) {
            return true;
        }
        for (int row = 0; row < getRowCount(); row++) {
            if (isDateTimeValue(getValueAt(row, column))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDateTimeValue(Object value) {
        if (value instanceof LocalDateTime || value instanceof Timestamp) {
            return true;
        }
        if (value instanceof CharSequence) {
            try {
                LocalDateTime.parse(value.toString().trim().replace(' ', 'T'));
                return true;
            } catch (DateTimeParseException exception) {
                return false;
            }
        }
        return false;
    }

    private static class ZebraDateTimeRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focused, int row, int column) {
            Component cell = super.getTableCellRendererComponent(
                    table, value, selected, focused, row, column);
            if (!selected) {
                cell.setBackground(row % 2 == 0
                        ? Theme.TABLE_BACKGROUND : Theme.ZEBRA_COLOR);
                cell.setForeground(Theme.TEXT_COLOR);
            }
            return cell;
        }

        @Override
        protected void setValue(Object value) {
            LocalDateTime dateTime = toDateTime(value);
            setText(dateTime == null
                    ? (value == null ? "" : value.toString())
                    : dateTime.format(DATE_TIME_FORMAT));
        }

        private LocalDateTime toDateTime(Object value) {
            if (value instanceof LocalDateTime) {
                return (LocalDateTime) value;
            }
            if (value instanceof Timestamp) {
                return ((Timestamp) value).toLocalDateTime();
            }
            if (value instanceof CharSequence) {
                try {
                    return LocalDateTime.parse(
                            value.toString().trim().replace(' ', 'T'));
                } catch (DateTimeParseException exception) {
                    return null;
                }
            }
            return null;
        }
    }
}
