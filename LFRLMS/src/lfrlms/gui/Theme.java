package lfrlms.gui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;

// Layer: GUI (presentation).
// Rubric: GUI, static/final.
/** Shared visual settings for tables, panels and dialogs. */
public final class Theme {
    public static final Color ACCENT_COLOR = new Color(49, 103, 158);
    public static final Color HEADER_COLOR = new Color(43, 58, 73);
    public static final Color ZEBRA_COLOR = new Color(241, 246, 250);
    public static final Color TEXT_COLOR = new Color(37, 48, 59);
    public static final Color TABLE_BACKGROUND = Color.WHITE;
    public static final Font BASE_FONT = new Font("SansSerif", Font.PLAIN, 13);
    public static final int ROW_HEIGHT = 28;
    public static final int PADDING = 10;

    private Theme() {
    }

    public static void applyPadding(JComponent component) {
        component.setBorder(BorderFactory.createEmptyBorder(
                PADDING, PADDING, PADDING, PADDING));
    }

    public static void applyDialogPadding(JDialog dialog) {
        if (dialog.getContentPane() instanceof JComponent) {
            applyPadding((JComponent) dialog.getContentPane());
        }
    }
}
