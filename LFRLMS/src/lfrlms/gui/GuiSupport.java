package lfrlms.gui;

import java.awt.Component;
import javax.swing.JOptionPane;
import lfrlms.exception.LfrlmsException;
import lfrlms.util.AppLogger;

/**
 * Purpose: Converts service failures into helpful messages for staff.
 * Concept: Exception handling and reusable Swing dialogs.
 * Layer: GUI (presentation).
 * Rubric: GUI, Exception handling.
 */
public final class GuiSupport {
    private GuiSupport() {
    }

    public static void showFailure(Component parent, String action,
            Exception failure, Runnable retry) {
        AppLogger.logException("GUI", action + " failed.", failure);
        if (isConnectionFailure(failure)) {
            showConnectionFailure(parent, retry);
            return;
        }
        String message = failure instanceof LfrlmsException
                ? failure.getMessage() : "The operation could not be completed.";
        if (message == null || message.trim().isEmpty()) {
            message = "The operation could not be completed.";
        }
        Object[] buttons = retry == null
                ? new Object[] {"Close"} : new Object[] {"Retry", "Close"};
        int choice = JOptionPane.showOptionDialog(parent, message,
                "Operation failed", JOptionPane.DEFAULT_OPTION,
                JOptionPane.ERROR_MESSAGE, null, buttons, buttons[0]);
        if (retry != null && choice == 0) {
            retry.run();
        }
    }

    public static void showConnectionFailure(Component parent, Runnable retry) {
        String message = "Cannot connect to the database. Check that MySQL is running "
                + "and config/db.properties is correct.";
        Object[] buttons = retry == null
                ? new Object[] {"Close"} : new Object[] {"Retry", "Close"};
        int choice = JOptionPane.showOptionDialog(parent, message,
                "Database unavailable", JOptionPane.DEFAULT_OPTION,
                JOptionPane.ERROR_MESSAGE, null, buttons, buttons[0]);
        if (retry != null && choice == 0) {
            retry.run();
        }
    }

    private static boolean isConnectionFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.toLowerCase().contains("could not connect")
                    || message.toLowerCase().contains("configuration is unavailable"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
