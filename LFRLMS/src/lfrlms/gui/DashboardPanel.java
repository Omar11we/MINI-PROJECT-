package lfrlms.gui;

import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import lfrlms.exception.LfrlmsException;
import lfrlms.service.AlertSnapshot;
import lfrlms.service.DashboardService;
import lfrlms.service.ReportService;
import lfrlms.util.AppLogger;
import java.util.Map;
import java.util.TreeMap;

/**
 * Purpose: Displays live fleet totals, revenue and alert details.
 * Concept: Swing layout managers and BackgroundTask for database work.
 * Layer: GUI (presentation).
 * Rubric: GUI, inner/anonymous classes.
 */
public class DashboardPanel extends JPanel {
    private final DashboardService dashboardService;
    private final ReportService reportService;
    private final BackgroundTask<DashboardData> refreshTask;
    private final HashMap<String, JLabel> values;
    private final DefaultListModel<String> alertItems;
    private final JLabel messageLabel;
    private final RevenueChart revenueChart;

    public DashboardPanel() {
        dashboardService = new DashboardService();
        reportService = new ReportService();
        refreshTask = new BackgroundTask<DashboardData>();
        values = new HashMap<String, JLabel>();
        alertItems = new DefaultListModel<String>();
        messageLabel = new JLabel(" ");
        revenueChart = new RevenueChart();
        buildPanel();
        refresh();
    }

    public void refresh() {
        messageLabel.setText("Loading dashboard...");
        refreshTask.execute(this, new BackgroundTask.Work<DashboardData>() {
            @Override
            public DashboardData run() throws LfrlmsException {
                HashMap<String, Object> summary = dashboardService.getDashboardSummary();
                LocalDate through = LocalDate.now();
                LocalDate from = through.withDayOfMonth(1).minusMonths(11);
                TreeMap<String, BigDecimal> revenue = reportService.revenueByMonth(from, through);
                fillMissingMonths(revenue, from, through);
                return new DashboardData(summary, revenue);
            }
        }, new BackgroundTask.Completion<DashboardData>() {
            @Override
            public void completed(DashboardData data, Exception failure) {
                if (failure != null) {
                    messageLabel.setText("Dashboard unavailable; check the database connection.");
                    AppLogger.logException(DashboardPanel.class.getName(),
                            "Dashboard refresh failed.", failure);
                    return;
                }
                updateValues(data.summary);
                revenueChart.setRevenue(data.revenue);
                messageLabel.setText(" ");
            }
        });
    }

    public void updateAlerts(AlertSnapshot snapshot) {
        alertItems.clear();
        for (String item : snapshot.getItems()) {
            alertItems.addElement(item);
        }
        if (alertItems.isEmpty()) {
            alertItems.addElement("No current alerts.");
        }
    }

    public void showAlertFailure() {
        alertItems.clear();
        alertItems.addElement("Alerts unavailable; check the database connection.");
    }

    private void buildPanel() {
        setLayout(new BorderLayout(8, 8));
        Theme.applyPadding(this);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(event -> refresh());
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(new JLabel("Fleet overview"), BorderLayout.WEST);
        top.add(messageLabel, BorderLayout.CENTER);
        top.add(refreshButton, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        JPanel cards = new JPanel(new GridLayout(3, 3, 8, 8));
        addCard(cards, "Total vehicles", "total_vehicles");
        addCard(cards, "Available vehicles", "available_vehicles");
        addCard(cards, "Rented vehicles", "rented_vehicles");
        addCard(cards, "Leased vehicles", "leased_vehicles");
        addCard(cards, "Maintenance vehicles", "maintenance_vehicles");
        addCard(cards, "Active rentals", "active_rentals");
        addCard(cards, "Active leases", "active_leases");
        addCard(cards, "Upcoming rentals", "upcoming_rentals");
        addCard(cards, "Revenue this month", "monthly_revenue");
        JPanel side = new JPanel(new BorderLayout(8, 8));
        side.add(revenueChart, BorderLayout.CENTER);

        JList<String> list = new JList<String>(alertItems);
        list.setVisibleRowCount(5);
        JScrollPane alertScroll = new JScrollPane(list);
        alertScroll.setBorder(BorderFactory.createTitledBorder("Current alerts"));
        side.add(alertScroll, BorderLayout.SOUTH);
        side.setPreferredSize(new java.awt.Dimension(410, 450));

        JPanel overview = new JPanel(new BorderLayout(8, 8));
        overview.add(cards, BorderLayout.CENTER);
        overview.add(side, BorderLayout.EAST);
        add(overview, BorderLayout.CENTER);
    }

    private void addCard(JPanel cards, String title, String key) {
        JPanel card = new JPanel(new BorderLayout(4, 8));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.HEADER_COLOR),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setForeground(Theme.HEADER_COLOR);
        JLabel valueLabel = new JLabel("—", SwingConstants.CENTER);
        valueLabel.setFont(Theme.BASE_FONT.deriveFont(Font.BOLD, 22.0f));
        valueLabel.setForeground(Theme.ACCENT_COLOR);
        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        cards.add(card);
        values.put(key, valueLabel);
    }

    private void updateValues(HashMap<String, Object> summary) {
        String[] keys = new String[] {
            "total_vehicles", "available_vehicles", "rented_vehicles",
            "leased_vehicles", "maintenance_vehicles", "active_rentals",
            "active_leases", "upcoming_rentals", "monthly_revenue"
        };
        for (String key : keys) {
            JLabel valueLabel = values.get(key);
            Object value = summary == null ? null : summary.get(key);
            valueLabel.setText(formatValue(value, "monthly_revenue".equals(key)));
        }
    }

    private void fillMissingMonths(TreeMap<String, BigDecimal> revenue,
            LocalDate from, LocalDate through) {
        DateTimeFormatter monthFormat = DateTimeFormatter.ofPattern("yyyy-MM");
        LocalDate month = from.withDayOfMonth(1);
        LocalDate lastMonth = through.withDayOfMonth(1);
        while (!month.isAfter(lastMonth)) {
            String key = month.format(monthFormat);
            if (!revenue.containsKey(key)) {
                revenue.put(key, BigDecimal.ZERO);
            }
            month = month.plusMonths(1);
        }
    }

    private String formatValue(Object value, boolean money) {
        if (value == null) {
            return money ? "0.00" : "0";
        }
        if (money && value instanceof BigDecimal) {
            return ((BigDecimal) value).setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        return value.toString();
    }

    /** Bundles the summary and chart values from one dashboard refresh task. */
    private static class DashboardData {
        private final HashMap<String, Object> summary;
        private final TreeMap<String, BigDecimal> revenue;

        private DashboardData(HashMap<String, Object> summary,
                TreeMap<String, BigDecimal> revenue) {
            this.summary = summary;
            this.revenue = revenue;
        }
    }

    /** Draws the monthly totals directly so the dashboard needs no chart dependency. */
    private static class RevenueChart extends JPanel {
        private static final DateTimeFormatter MONTH_LABEL =
                DateTimeFormatter.ofPattern("MMM yy");
        private TreeMap<String, BigDecimal> revenue;

        private RevenueChart() {
            revenue = new TreeMap<String, BigDecimal>();
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createTitledBorder("Revenue by month (last 12 months)"));
            setPreferredSize(new java.awt.Dimension(390, 300));
        }

        private void setRevenue(TreeMap<String, BigDecimal> values) {
            revenue = new TreeMap<String, BigDecimal>(values);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D canvas = (Graphics2D) graphics.create();
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            drawChart(canvas);
            canvas.dispose();
        }

        private void drawChart(Graphics2D canvas) {
            java.awt.Insets insets = getInsets();
            int left = insets.left + 58;
            int right = getWidth() - insets.right - 16;
            int top = insets.top + 16;
            int bottom = getHeight() - insets.bottom - 68;
            if (right <= left || bottom <= top) {
                return;
            }
            BigDecimal maximum = maximumRevenue();
            if (maximum.compareTo(BigDecimal.ZERO) <= 0) {
                maximum = BigDecimal.ONE;
            }
            drawAxes(canvas, left, right, top, bottom, maximum);
            if (revenue.isEmpty() || !hasRevenue()) {
                canvas.setColor(Theme.TEXT_COLOR);
                canvas.drawString("No revenue recorded for this period.", left + 8,
                        top + (bottom - top) / 2);
                return;
            }
            drawBars(canvas, left, right, top, bottom, maximum);
        }

        private BigDecimal maximumRevenue() {
            BigDecimal maximum = BigDecimal.ZERO;
            for (BigDecimal amount : revenue.values()) {
                if (amount != null && amount.compareTo(maximum) > 0) {
                    maximum = amount;
                }
            }
            return maximum;
        }

        private boolean hasRevenue() {
            for (BigDecimal amount : revenue.values()) {
                if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                    return true;
                }
            }
            return false;
        }

        private void drawAxes(Graphics2D canvas, int left, int right,
                int top, int bottom, BigDecimal maximum) {
            canvas.setFont(Theme.BASE_FONT.deriveFont(10.5f));
            FontMetrics metrics = canvas.getFontMetrics();
            canvas.setStroke(new BasicStroke(1.0f));
            for (int tick = 0; tick <= 4; tick++) {
                int y = bottom - (bottom - top) * tick / 4;
                BigDecimal value = maximum.multiply(BigDecimal.valueOf(tick))
                        .divide(BigDecimal.valueOf(4), MathContext.DECIMAL64);
                canvas.setColor(new Color(224, 230, 235));
                canvas.drawLine(left, y, right, y);
                canvas.setColor(Theme.TEXT_COLOR);
                String label = compactAmount(value);
                canvas.drawString(label, left - metrics.stringWidth(label) - 6, y + 4);
            }
            canvas.setColor(Theme.HEADER_COLOR);
            canvas.setStroke(new BasicStroke(1.4f));
            canvas.drawLine(left, top, left, bottom);
            canvas.drawLine(left, bottom, right, bottom);
        }

        private void drawBars(Graphics2D canvas, int left, int right,
                int top, int bottom, BigDecimal maximum) {
            int count = revenue.size();
            int plotWidth = right - left;
            Font oldFont = canvas.getFont();
            canvas.setFont(Theme.BASE_FONT.deriveFont(9.0f));
            FontMetrics metrics = canvas.getFontMetrics();
            int index = 0;
            for (Map.Entry<String, BigDecimal> entry : revenue.entrySet()) {
                BigDecimal amount = entry.getValue() == null
                        ? BigDecimal.ZERO : entry.getValue();
                int slotStart = left + plotWidth * index / count;
                int slotEnd = left + plotWidth * (index + 1) / count;
                int slotWidth = Math.max(1, slotEnd - slotStart);
                int barWidth = Math.max(8, slotWidth * 58 / 100);
                int height = amount.signum() <= 0 ? 0
                        : amount.multiply(BigDecimal.valueOf(bottom - top))
                                .divide(maximum, 0, RoundingMode.HALF_UP).intValue();
                int x = slotStart + (slotWidth - barWidth) / 2;
                int y = bottom - height;
                canvas.setColor(Theme.ACCENT_COLOR);
                canvas.fillRoundRect(x, y, barWidth, height, 5, 5);
                String valueLabel = compactAmount(amount);
                int labelX = x + (barWidth - metrics.stringWidth(valueLabel)) / 2;
                canvas.setColor(Theme.HEADER_COLOR);
                canvas.drawString(valueLabel, labelX, Math.max(top + 10, y - 4));
                drawMonthLabel(canvas, entry.getKey(), slotStart + slotWidth / 2,
                        bottom + 18);
                index++;
            }
            canvas.setFont(oldFont);
        }

        private void drawMonthLabel(Graphics2D canvas, String month,
                int centerX, int baseline) {
            LocalDate date = LocalDate.parse(month + "-01");
            String label = date.format(MONTH_LABEL);
            FontMetrics metrics = canvas.getFontMetrics();
            java.awt.geom.AffineTransform oldTransform = canvas.getTransform();
            canvas.rotate(-Math.PI / 4.0, centerX, baseline);
            canvas.setColor(Theme.TEXT_COLOR);
            canvas.drawString(label, centerX - metrics.stringWidth(label) / 2, baseline);
            canvas.setTransform(oldTransform);
        }

        private String compactAmount(BigDecimal amount) {
            BigDecimal absolute = amount.abs();
            if (absolute.compareTo(new BigDecimal("1000000000")) >= 0) {
                return amount.divide(new BigDecimal("1000000000"), 2,
                        RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "B";
            }
            if (absolute.compareTo(new BigDecimal("1000000")) >= 0) {
                return amount.divide(new BigDecimal("1000000"), 2,
                        RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "M";
            }
            if (absolute.compareTo(new BigDecimal("1000")) >= 0) {
                return amount.divide(new BigDecimal("1000"), 1,
                        RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "K";
            }
            return amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
        }
    }
}
