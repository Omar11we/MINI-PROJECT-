package lfrlms.gui;

import java.awt.Component;
import java.awt.Cursor;
import javax.swing.SwingUtilities;

/**
 * Purpose: Runs one service operation away from Swing's event thread.
 * Concept: Thread, inner classes and safe Event Dispatch Thread updates.
 * Layer: GUI (presentation).
 * Rubric: Thread, inner/anonymous classes.
 */
public class BackgroundTask<T> {
    public interface Work<T> {
        T run() throws Exception;
    }

    public interface Completion<T> {
        void completed(T result, Exception failure);
    }

    private int requestNumber;

    public void execute(final Component waitTarget, final Work<T> work,
            final Completion<T> completion) {
        // Number this request so an older result can be recognised later
        final int requestId = nextRequest();
        // Show the wait cursor on the Event Dispatch Thread
        if (waitTarget != null) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    waitTarget.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                }
            });
        }
        // Run the work on its own thread so the window stays responsive
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                // Capture either the result or the failure
                T result = null;
                Exception failure = null;
                try {
                    result = work.run();
                } catch (Exception exception) {
                    failure = exception;
                }
                final T completedResult = result;
                final Exception completedFailure = failure;
                // Hand the outcome back to the Event Dispatch Thread
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        // Skip stale results from an older or cancelled request
                        if (!isCurrent(requestId)) {
                            return;
                        }
                        // Restore the cursor and report the outcome
                        if (waitTarget != null) {
                            waitTarget.setCursor(Cursor.getDefaultCursor());
                        }
                        completion.completed(completedResult, completedFailure);
                    }
                });
            }
        }, "lfrlms-background-" + requestId);
        // Daemon thread, so an unfinished task never keeps the application open
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized void ignorePendingResults() {
        requestNumber++;
    }

    private synchronized int nextRequest() {
        requestNumber++;
        return requestNumber;
    }

    private synchronized boolean isCurrent(int requestId) {
        return requestNumber == requestId;
    }
}
