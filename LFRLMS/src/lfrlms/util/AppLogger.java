package lfrlms.util;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Purpose: Records diagnostic details without showing raw failures as user messages.
 * Concept: Static members and exception logging.
 * Layer: Utility.
 * Rubric: static/final.
 */
public final class AppLogger {
    private static final Logger LOGGER = Logger.getLogger("lfrlms");

    private AppLogger() {
    }

    public static void logInfo(String message) {
        LOGGER.info(message);
    }

    public static void logException(String source, String message, Throwable cause) {
        LOGGER.log(Level.SEVERE, source + ": " + message, cause);
    }
}
