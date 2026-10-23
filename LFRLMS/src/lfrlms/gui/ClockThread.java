package lfrlms.gui;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * Purpose: Refreshes the status-bar clock without touching Swing off the EDT.
 * Concept: Thread, volatile state, sleep and interrupt handling.
 * Layer: GUI (presentation).
 * Rubric: Thread, Inheritance.
 */
public class ClockThread extends Thread {
    private static final DateTimeFormatter CLOCK_FORMAT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss");
    private final JLabel clockLabel;
    // volatile so a stop request from the GUI thread is seen at once by this clock thread
    private volatile boolean running;

    public ClockThread(JLabel clockLabel) {
        super("lfrlms-clock");
        this.clockLabel = clockLabel;
        running = true;
        setDaemon(true);
    }

    @Override
    public void run() {
        System.out.println("ClockThread started.");
        try {
            while (running) {
                final String timeText = LocalDateTime.now().format(CLOCK_FORMAT);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        clockLabel.setText(timeText);
                    }
                });
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException exception) {
                    if (running) {
                        Thread.currentThread().interrupt();
                    }
                    break;
                }
            }
        } finally {
            System.out.println("ClockThread stopped.");
        }
    }

    public void requestStop() {
        running = false;
        interrupt();
    }
}
