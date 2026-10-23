package lfrlms.gui;

import java.awt.Component;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import lfrlms.util.AppLogger;

/**
 * Purpose: Writes the visible page of a read-only table as escaped CSV.
 * Concept: BufferedWriter, exception handling and reusable GUI support.
 * Layer: GUI (presentation).
 * Rubric: GUI, Exception handling.
 */
public final class CsvExport {
    private CsvExport() {
    }

    public static void exportCurrentPage(Component parent, JTable table, String fileName) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(fileName));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File selected = addCsvExtension(chooser.getSelectedFile());
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(selected))) {
            for (int column = 0; column < table.getColumnCount(); column++) {
                if (column > 0) {
                    writer.write(',');
                }
                writer.write(escape(table.getColumnName(column)));
            }
            writer.newLine();
            for (int row = 0; row < table.getRowCount(); row++) {
                for (int column = 0; column < table.getColumnCount(); column++) {
                    if (column > 0) {
                        writer.write(',');
                    }
                    Object value = table.getValueAt(row, column);
                    writer.write(escape(value == null ? "" : value.toString()));
                }
                writer.newLine();
            }
            JOptionPane.showMessageDialog(parent,
                    "Exported the visible page to " + selected.getName() + ".");
        } catch (IOException exception) {
            AppLogger.logException(CsvExport.class.getName(), "CSV export failed.", exception);
            JOptionPane.showMessageDialog(parent,
                    "The CSV file could not be written.", "Export failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static File addCsvExtension(File file) {
        if (file.getName().toLowerCase().endsWith(".csv")) {
            return file;
        }
        return new File(file.getParentFile(), file.getName() + ".csv");
    }

    private static String escape(String value) {
        String escaped = value.replace("\"", "\"\"");
        if (escaped.indexOf(',') >= 0 || escaped.indexOf('"') >= 0
                || escaped.indexOf('\n') >= 0 || escaped.indexOf('\r') >= 0) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
