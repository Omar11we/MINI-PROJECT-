package lfrlms.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import lfrlms.exception.LfrlmsException;
import lfrlms.service.AlertService;
import lfrlms.service.AlertSnapshot;
import lfrlms.util.AppLogger;

/**
 * Purpose: Hosts the application modules and a live database status bar.
 * Concept: Swing tabs, layout managers and safe window shutdown.
 * Layer: GUI (presentation).
 * Rubric: GUI, Thread.
 */
public class MainFrame extends JFrame {
    private final JLabel clockLabel;
    private final JLabel databaseLabel;
    private final JLabel alertLabel;
    private final JLabel messageLabel;
    private final DashboardPanel dashboardPanel;
    private final ClockThread clockThread;
    private final AlertMonitor alertMonitor;
    private final Thread alertMonitorThread;

    public MainFrame() {
        super("Luxury Fleet Rental & Leasing Management System");
        clockLabel = new JLabel();
        databaseLabel = new JLabel("Database: checking...");
        alertLabel = new JLabel("Alerts: checking...");
        messageLabel = new JLabel(" ");
        dashboardPanel = new DashboardPanel();
        clockThread = new ClockThread(clockLabel);
        alertMonitor = new AlertMonitor(new AlertService(), new AlertMonitor.Listener() {
            @Override
            public void alertsUpdated(AlertSnapshot snapshot) {
                databaseLabel.setText("Database: connected");
                alertLabel.setText(alertSummary(snapshot.getCounts()));
                messageLabel.setText(" ");
                dashboardPanel.updateAlerts(snapshot);
            }

            @Override
            public void alertCheckFailed(LfrlmsException failure) {
                databaseLabel.setText("Database: unavailable");
                alertLabel.setText("Alerts: unavailable");
                messageLabel.setText(
                        "Cannot connect. Check MySQL and config/db.properties.");
                dashboardPanel.showAlertFailure();
            }
        });
        alertMonitorThread = new Thread(alertMonitor, "lfrlms-alert-monitor");
        alertMonitorThread.setDaemon(true);
        buildWindow();
        clockThread.start();
        alertMonitorThread.start();
    }

    private void buildWindow() {
        setLayout(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Dashboard", dashboardPanel);
        tabs.addTab("Vehicles", new VehiclesPanel());
        tabs.addTab("Customers", new CustomersPanel());
        tabs.addTab("Rentals", new RentalsPanel());
        tabs.addTab("Leases", new LeasesPanel());
        tabs.addTab("Payments", new PaymentsPanel());
        tabs.addTab("Maintenance", new MaintenancePanel());
        tabs.addTab("Drivers", new DriversPanel());
        tabs.addTab("Reports", new ReportsPanel());
        tabs.addTab("DBMS Insights", new DbmsInsightsPanel());
        add(tabs, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                stopBackgroundThreads();
                dispose();
            }
        });
        setSize(1200, 750);
        setMinimumSize(new java.awt.Dimension(960, 620));
        setLocationRelativeTo(null);
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        Theme.applyPadding(bar);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        left.add(clockLabel);
        left.add(databaseLabel);
        left.add(alertLabel);
        JButton retryButton = new JButton("Retry connection");
        retryButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                databaseLabel.setText("Database: checking...");
                messageLabel.setText("Checking database...");
                dashboardPanel.refresh();
                alertMonitorThread.interrupt();
            }
        });
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 3));
        right.add(messageLabel);
        right.add(retryButton);
        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel placeholder(String text) {
        JPanel panel = new JPanel(new BorderLayout());
        Theme.applyPadding(panel);
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    private String alertSummary(HashMap<String, Integer> summary) {
        return "Alerts - Overdue " + count(summary, "overdueRentals")
                + " | Upcoming " + count(summary, "upcomingRentals")
                + " | Leases " + count(summary, "leasesEnding")
                + " | Maintenance " + count(summary, "maintenanceDue")
                + " | Licences " + count(summary, "licensesExpiring");
    }

    private int count(HashMap<String, Integer> summary, String name) {
        Integer value = summary.get(name);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private void stopBackgroundThreads() {
        // Ask both loops to stop; the volatile flags make this visible at once
        clockThread.requestStop();
        alertMonitor.requestStop();
        // Wake the alert thread from its 60-second sleep (requestStop interrupts the clock)
        alertMonitorThread.interrupt();
        // Wait at most 500 ms per thread so closing the window cannot hang
        joinBriefly(clockThread);
        joinBriefly(alertMonitorThread);
    }

    private void joinBriefly(Thread thread) {
        try {
            thread.join(500L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            AppLogger.logException(MainFrame.class.getName(),
                    "Interrupted while stopping background threads.", exception);
        }
    }
}
