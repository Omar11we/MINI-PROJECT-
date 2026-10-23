package lfrlms.gui;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Purpose: Gives vehicle status values a clear visual meaning in tables.
 * Concept: Swing renderer and conditional display.
 * Layer: GUI (presentation).
 * Rubric: GUI, Inheritance.
 */
public class StatusColourRenderer extends DefaultTableCellRenderer {
    private static final Color GREEN = new Color(220, 245, 226);
    private static final Color AMBER = new Color(255, 232, 184);
    private static final Color BLUE = new Color(205, 222, 255);
    private static final Color RED = new Color(255, 214, 214);
    private static final Color GREY = new Color(226, 229, 233);

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean selected, boolean focused, int row, int column) {
        Component cell = super.getTableCellRendererComponent(
                table, value, selected, focused, row, column);
        if (!selected) {
            String status = value == null ? "" : value.toString();
            cell.setBackground(statusColor(status, row));
            cell.setForeground(Theme.TEXT_COLOR);
        }
        return cell;
    }

    private Color statusColor(String status, int row) {
        if ("Available".equals(status) || "Active".equals(status)
                || "Success".equals(status) || "Complete".equals(status)
                || "Completed".equals(status)) {
            return GREEN;
        }
        if ("Rented".equals(status) || "Pending".equals(status)
                || "In Progress".equals(status) || "Scheduled".equals(status)
                || "Confirmed".equals(status)) {
            return AMBER;
        }
        if ("Leased".equals(status) || "Reserved".equals(status)) {
            return BLUE;
        }
        if ("Maintenance".equals(status) || "Failed".equals(status)
                || "Cancelled".equals(status) || "Terminated".equals(status)) {
            return RED;
        }
        if ("Inactive".equals(status) || "Refunded".equals(status)) {
            return GREY;
        }
        return row % 2 == 0 ? Theme.TABLE_BACKGROUND : Theme.ZEBRA_COLOR;
    }
}
