package lfrlms.gui;

import javax.swing.SwingUtilities;
import lfrlms.exception.LfrlmsException;
import lfrlms.service.AlertService;
import lfrlms.service.AlertSnapshot;
import lfrlms.util.AppLogger;

/**
 * Purpose: Polls service-layer alerts without holding a connection while idle.
 * Concept: Runnable, volatile state, sleep and interrupt handling.
 * Layer: GUI (presentation).
 * Rubric: Thread, inner/anonymous classes.
 */
public class AlertMonitor implements Runnable {
    public static final long POLL_INTERVAL_MILLIS = 60000L;

    public interface Listener {
        void alertsUpdated(AlertSnapshot snapshot);
        void alertCheckFailed(LfrlmsException failure);
    }

    private final AlertService alertService;
    private final Listener listener;
    // volatile so a stop request from the GUI thread is seen at once by this polling thread
    private volatile boolean running;

    public AlertMonitor(AlertService alertService, Listener listener) {
        this.alertService = alertService;
        this.listener = listener;
        running = true;
    }

    @Override
    public void run() {
        System.out.println("AlertMonitor started.");
        try {
            while (running) {
                checkAlerts();
                if (!running) {
                    break;
                }
                try {
                    Thread.sleep(POLL_INTERVAL_MILLIS);
                } catch (InterruptedException exception) {
                    if (running) {
                        continue;
                    }
                    break;
                }
            }
        } finally {
            System.out.println("AlertMonitor stopped.");
        }
    }

    public void requestStop() {
        running = false;
    }

    private void checkAlerts() {
        try {
            final AlertSnapshot snapshot = alertService.getAlertSnapshot();
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    listener.alertsUpdated(snapshot);
                }
            });
        } catch (final LfrlmsException failure) {
            AppLogger.logException(AlertMonitor.class.getName(),
                    "Periodic alert check failed.", failure);
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    listener.alertCheckFailed(failure);
                }
            });
        }
    }
}
