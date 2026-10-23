package lfrlms.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import lfrlms.exception.LfrlmsException;
import lfrlms.model.Branch;
import lfrlms.model.VehicleCategory;
import lfrlms.service.LookupService;
import lfrlms.service.ReportService;
import lfrlms.util.Constants;
import lfrlms.util.DateUtil;

/**
 * Purpose: Presents the nine database reports with filters and CSV export.
 * Concept: Swing tabs, shared table models and background service calls.
 * Layer: GUI (presentation).
 * Rubric: GUI, inner/anonymous classes.
 */
public class ReportsPanel extends JPanel {
    private final ReportService reportService;
    private final LookupService lookupService;
    private final BackgroundTask<LookupData> lookupTask;
    private final JTabbedPane reportTabs;
    private final AvailableVehiclesReport availableVehiclesReport;

    public ReportsPanel() {
        reportService = new ReportService();
        lookupService = new LookupService();
        lookupTask = new BackgroundTask<LookupData>();
        reportTabs = new JTabbedPane();
        availableVehiclesReport = new AvailableVehiclesReport();
        buildPanel();
        loadLookups();
    }

    private void buildPanel() {
        setLayout(new BorderLayout());
        Theme.applyPadding(this);
        reportTabs.addTab("R-01 Available vehicles", availableVehiclesReport);
        reportTabs.addTab("R-02 Active rentals", new ActiveRentalsReport());
        reportTabs.addTab("R-03 Active leases", new ActiveLeasesReport());
        reportTabs.addTab("R-04 Monthly revenue", new RevenueReport());
        reportTabs.addTab("R-05 Customer balances", new OutstandingBalancesReport());
        reportTabs.addTab("R-06 Most rented", new MostRentedVehiclesReport());
        reportTabs.addTab("R-07 Maintenance due", new MaintenanceDueReport());
        reportTabs.addTab("R-08 Customer history", new CustomerHistoryReport());
        reportTabs.addTab("R-09 Rental status", new RentalStatusReport());
        reportTabs.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent event) {
                Component selected = reportTabs.getSelectedComponent();
                if (selected instanceof ReportTablePanel) {
                    ((ReportTablePanel) selected).loadReport();
                }
            }
        });
        add(reportTabs, BorderLayout.CENTER);
    }

    private void loadLookups() {
        lookupTask.execute(this, new BackgroundTask.Work<LookupData>() {
            @Override
            public LookupData run() throws LfrlmsException {
                return new LookupData(lookupService.listBranches(),
                        lookupService.listCategories());
            }
        }, new BackgroundTask.Completion<LookupData>() {
            @Override
            public void completed(LookupData result, Exception failure) {
                if (failure != null) {
                    GuiSupport.showFailure(ReportsPanel.this, "Load report filters",
                            failure, new Runnable() {
                                @Override
                                public void run() {
                                    loadLookups();
                                }
                            });
                    return;
                }
                availableVehiclesReport.setLookups(result);
            }
        });
    }

    private abstract class ReportTablePanel extends JPanel {
        private final String reportName;
        private final BackgroundTask<ReportRows> reportTask;
        private final SharedTableModel tableModel;
        private final JTable table;
        private final PaginationBar pagination;
        private final JLabel messageLabel;
        private final JPanel emptyFilters;

        private ReportTablePanel(String reportName, String[] columns, boolean paged) {
            super(new BorderLayout(8, 8));
            this.reportName = reportName;
            reportTask = new BackgroundTask<ReportRows>();
            tableModel = new SharedTableModel(columns);
            table = SharedTableModel.createTable(tableModel);
            pagination = new PaginationBar();
            pagination.setVisible(paged);
            messageLabel = new JLabel(" ");
            emptyFilters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
            Theme.applyPadding(this);
            table.setFillsViewportHeight(true);
            add(emptyFilters, BorderLayout.NORTH);
            add(new JScrollPane(table), BorderLayout.CENTER);
            add(buildFooter(), BorderLayout.SOUTH);
            pagination.setPageChangeListener(new PaginationBar.PageChangeListener() {
                @Override
                public void pageChanged(int pageNumber, int pageSize) {
                    loadReport();
                }
            });
        }

        protected abstract JPanel buildFilterBar();

        protected abstract ReportJob createJob(int pageNumber, int pageSize)
                throws Exception;

        protected final void installFilters() {
            remove(emptyFilters);
            add(buildFilterBar(), BorderLayout.NORTH);
        }

        protected final void refreshFromFirstPage() {
            refreshFromFirstPage(true);
        }

        protected final void refreshFromFirstPage(boolean explicitSubmission) {
            pagination.setPageNumber(1);
            loadReport(explicitSubmission);
        }

        protected boolean canLoad() {
            return true;
        }

        private JPanel buildFooter() {
            JPanel footer = new JPanel(new BorderLayout(8, 0));
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
            JButton refreshButton = new JButton("Refresh");
            JButton exportButton = new JButton("Export CSV");
            refreshButton.addActionListener(event -> refreshFromFirstPage());
            exportButton.addActionListener(event ->
                    CsvExport.exportCurrentPage(this, table, fileName(reportName)));
            actions.add(refreshButton);
            actions.add(exportButton);
            footer.add(actions, BorderLayout.WEST);
            footer.add(messageLabel, BorderLayout.CENTER);
            footer.add(pagination, BorderLayout.EAST);
            return footer;
        }

        protected final void loadReport() {
            loadReport(false);
        }

        protected final void loadReport(boolean explicitSubmission) {
            if (!canLoad()) {
                messageLabel.setText("Loading report filters...");
                return;
            }
            if (!explicitSubmission && !hasRequiredInput()) {
                clearRows(getMissingInputHint());
                return;
            }
            final int pageNumber = pagination.getPageNumber();
            final int pageSize = pagination.getPageSize();
            final ReportJob job;
            try {
                job = createJob(pageNumber, pageSize);
            } catch (Exception exception) {
                clearRows(exception.getMessage());
                if (explicitSubmission) {
                    JOptionPane.showMessageDialog(this, exception.getMessage(),
                            "Check report filters", JOptionPane.WARNING_MESSAGE);
                }
                return;
            }
            messageLabel.setText("Loading " + reportName + "...");
            reportTask.execute(this, new BackgroundTask.Work<ReportRows>() {
                @Override
                public ReportRows run() throws Exception {
                    return job.load();
                }
            }, new BackgroundTask.Completion<ReportRows>() {
                @Override
                public void completed(ReportRows result, Exception failure) {
                    if (failure != null) {
                        messageLabel.setText(" ");
                        GuiSupport.showFailure(ReportTablePanel.this,
                                "Load " + reportName, failure, new Runnable() {
                                    @Override
                                    public void run() {
                                        loadReport();
                                    }
                                });
                        return;
                    }
                    pagination.setTotalRows(result.totalRows);
                    if (pagination.getPageNumber() != pageNumber) {
                        loadReport();
                        return;
                    }
                    tableModel.replaceRows(result.rows);
                    messageLabel.setText(result.totalRows + " rows");
                }
            });
        }

        protected boolean hasRequiredInput() {
            return true;
        }

        protected String getMissingInputHint() {
            return "Enter the required filters and press Enter or Refresh.";
        }

        protected final void addEnterReload(JTextField field) {
            field.addActionListener(event -> refreshFromFirstPage(true));
        }

        private void clearRows(String message) {
            reportTask.ignorePendingResults();
            tableModel.replaceRows(new Vector<Vector<Object>>());
            pagination.setTotalRows(0);
            messageLabel.setText(message == null || message.trim().isEmpty()
                    ? "Enter the required filters and press Enter or Refresh."
                    : message);
        }

        private String fileName(String name) {
            return name.toLowerCase().replace(' ', '-') + ".csv";
        }
    }

    private class AvailableVehiclesReport extends ReportTablePanel {
        private final JTextField fromField;
        private final JTextField toField;
        private final JComboBox<LookupChoice> branchBox;
        private final JComboBox<LookupChoice> categoryBox;
        private boolean lookupsReady;

        private AvailableVehiclesReport() {
            super("R-01 Available vehicles", new String[] {"Vehicle ID", "Registration",
                "Brand", "Category", "Branch", "City", "Model", "Year",
                "Rental / day", "Lease / month", "Status"}, false);
            fromField = new JTextField(18);
            toField = new JTextField(18);
            DateTimeFormatter displayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
            fromField.setText(now.format(displayFormat));
            toField.setText(now.plusDays(1).format(displayFormat));
            fromField.setToolTipText("Format: yyyy-MM-dd HH:mm");
            toField.setToolTipText("Format: yyyy-MM-dd HH:mm");
            addEnterReload(fromField);
            addEnterReload(toField);
            branchBox = new JComboBox<LookupChoice>();
            categoryBox = new JComboBox<LookupChoice>();
            branchBox.addItem(new LookupChoice(0, "All branches"));
            categoryBox.addItem(new LookupChoice(0, "All categories"));
            lookupsReady = false;
            installFilters();
        }

        private void setLookups(LookupData data) {
            for (Branch branch : data.branches) {
                branchBox.addItem(new LookupChoice(branch.getBranchId(), branch.getBranchName()));
            }
            for (VehicleCategory category : data.categories) {
                categoryBox.addItem(new LookupChoice(category.getCategoryId(),
                        category.getCategoryName()));
            }
            branchBox.addActionListener(event -> refreshFromFirstPage(false));
            categoryBox.addActionListener(event -> refreshFromFirstPage(false));
            lookupsReady = true;
            loadReport();
        }

        @Override
        protected JPanel buildFilterBar() {
            JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
            Theme.applyPadding(filters);
            filters.add(new JLabel("Available from (yyyy-MM-dd HH:mm)"));
            filters.add(fromField);
            filters.add(new JLabel("to"));
            filters.add(toField);
            filters.add(new JLabel("Branch"));
            filters.add(branchBox);
            filters.add(new JLabel("Category"));
            filters.add(categoryBox);
            return filters;
        }

        @Override
        protected boolean canLoad() {
            return lookupsReady;
        }

        @Override
        protected ReportJob createJob(int pageNumber, int pageSize) {
            final LocalDateTime from = parseDateTime(fromField.getText(), "from");
            final LocalDateTime to = parseDateTime(toField.getText(), "to");
            if (from == null || to == null || !to.isAfter(from)) {
                throw new IllegalArgumentException(
                        "Enter both dates; the end must be after the start.");
            }
            final Integer branchId = selectedId(branchBox);
            final Integer categoryId = selectedId(categoryBox);
            return new ReportJob() {
                @Override
                public ReportRows load() throws LfrlmsException {
                    ArrayList<HashMap<String, Object>> data = reportService.availableVehicles(
                            from, to, branchId, categoryId);
                    Vector<Vector<Object>> rows = new Vector<Vector<Object>>();
                    for (HashMap<String, Object> row : data) {
                        rows.add(vector(row, "vehicle_id", "registration_no", "brand_name",
                                "category_name", "branch_name", "branch_city", "model",
                                "model_year", "rental_rate", "lease_rate", "status"));
                    }
                    return new ReportRows(rows, rows.size());
                }
            };
        }
    }

    private class ActiveRentalsReport extends ReportTablePanel {
        private ActiveRentalsReport() {
            super("R-02 Active rentals", new String[] {"Rental ID", "Customer ID",
                "First name", "Last name", "Vehicle ID", "Registration", "Model",
                "Scheduled start", "Expected return", "Actual pickup", "Status"}, true);
            installFilters();
        }

        @Override
        protected JPanel buildFilterBar() {
            return infoBar("Status filter: Active rentals");
        }

        @Override
        protected ReportJob createJob(final int pageNumber, final int pageSize) {
            return new ReportJob() {
                @Override
                public ReportRows load() throws LfrlmsException {
                    long count = reportService.countActiveRentals();
                    ArrayList<HashMap<String, Object>> data =
                            reportService.activeRentals(pageNumber, pageSize);
                    return new ReportRows(mapRows(data, new String[] {"rental_id",
                        "customer_id", "first_name", "last_name", "vehicle_id",
                        "registration_no", "model", "scheduled_start_datetime",
                        "expected_return_datetime", "actual_pickup_datetime", "status"}),
                            count);
                }
            };
        }
    }

    private class ActiveLeasesReport extends ReportTablePanel {
        private ActiveLeasesReport() {
            super("R-03 Active leases", new String[] {"Lease ID", "Customer ID",
                "First name", "Last name", "Vehicle ID", "Registration", "Model",
                "Start date", "End date", "Monthly charge", "Deposit", "Status"}, true);
            installFilters();
        }

        @Override
        protected JPanel buildFilterBar() {
            return infoBar("Active leases, ordered by the nearest end date");
        }

        @Override
        protected ReportJob createJob(final int pageNumber, final int pageSize) {
            return new ReportJob() {
                @Override
                public ReportRows load() throws LfrlmsException {
                    long count = reportService.countActiveLeases();
                    ArrayList<HashMap<String, Object>> data =
                            reportService.activeLeases(pageNumber, pageSize);
                    return new ReportRows(mapRows(data, new String[] {"lease_id",
                        "customer_id", "first_name", "last_name", "vehicle_id",
                        "registration_no", "model", "start_date", "end_date",
                        "monthly_charge", "deposit", "status"}), count);
                }
            };
        }
    }

    private class RevenueReport extends ReportTablePanel {
        private final JTextField fromField;
        private final JTextField toField;

        private RevenueReport() {
            super("R-04 Monthly revenue", new String[] {"Revenue month", "Revenue"}, false);
            fromField = new JTextField(10);
            toField = new JTextField(10);
            fromField.setText(LocalDate.now().withDayOfMonth(1).toString());
            toField.setText(LocalDate.now().toString());
            addEnterReload(fromField);
            addEnterReload(toField);
            installFilters();
        }

        @Override
        protected JPanel buildFilterBar() {
            JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
            Theme.applyPadding(filters);
            filters.add(new JLabel("From (yyyy-MM-dd)"));
            filters.add(fromField);
            filters.add(new JLabel("To (yyyy-MM-dd)"));
            filters.add(toField);
            return filters;
        }

        @Override
        protected ReportJob createJob(int pageNumber, int pageSize) {
            final LocalDate from = parseDate(fromField.getText(), "from");
            final LocalDate to = parseDate(toField.getText(), "to");
            if (from == null || to == null || to.isBefore(from)) {
                throw new IllegalArgumentException(
                        "Enter both dates; the end date cannot be before the start date.");
            }
            return new ReportJob() {
                @Override
                public ReportRows load() throws LfrlmsException {
                    TreeMap<String, java.math.BigDecimal> revenue =
                            reportService.revenueByMonth(from, to);
                    Vector<Vector<Object>> rows = new Vector<Vector<Object>>();
                    for (Map.Entry<String, java.math.BigDecimal> entry : revenue.entrySet()) {
                        rows.add(vector(entry.getKey(), entry.getValue()));
                    }
                    return new ReportRows(rows, rows.size());
                }
            };
        }
    }

    private class OutstandingBalancesReport extends ReportTablePanel {
        private OutstandingBalancesReport() {
            super("R-05 Customer balances", new String[] {"Customer ID", "Type",
                "First name", "Last name", "Outstanding"}, true);
            installFilters();
        }

        @Override
        protected JPanel buildFilterBar() {
            return infoBar("Outstanding balance per customer");
        }

        @Override
        protected ReportJob createJob(final int pageNumber, final int pageSize) {
            return new ReportJob() {
                @Override
                public ReportRows load() throws LfrlmsException {
                    long count = reportService.countOutstandingBalances();
                    ArrayList<HashMap<String, Object>> data =
                            reportService.outstandingBalances(pageNumber, pageSize);
                    return new ReportRows(mapRows(data, new String[] {"customer_id",
                        "customer_type", "first_name", "last_name", "outstanding"}), count);
                }
            };
        }
    }

    private class MostRentedVehiclesReport extends ReportTablePanel {
        private final JComboBox<Integer> limitBox;

        private MostRentedVehiclesReport() {
            super("R-06 Most rented vehicles", new String[] {"Vehicle ID", "Registration",
                "Brand", "Model", "Rental count"}, false);
            limitBox = new JComboBox<Integer>(new Integer[] {10, 25, 50});
            limitBox.addActionListener(event -> refreshFromFirstPage(false));
            installFilters();
        }

        @Override
        protected JPanel buildFilterBar() {
            JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
            Theme.applyPadding(filters);
            filters.add(new JLabel("Show top"));
            filters.add(limitBox);
            filters.add(new JLabel("vehicles"));
            return filters;
        }

        @Override
        protected ReportJob createJob(int pageNumber, int pageSize) {
            final int limit = ((Integer) limitBox.getSelectedItem()).intValue();
            return new ReportJob() {
                @Override
                public ReportRows load() throws LfrlmsException {
                    ArrayList<HashMap<String, Object>> data =
                            reportService.mostRentedVehicles(limit);
                    return new ReportRows(mapRows(data, new String[] {"vehicle_id",
                        "registration_no", "brand_name", "model", "rental_count"}),
                            data.size());
                }
            };
        }
    }

    private class MaintenanceDueReport extends ReportTablePanel {
        private final JTextField throughField;

        private MaintenanceDueReport() {
            super("R-07 Maintenance due", new String[] {"Maintenance ID", "Vehicle ID",
                "Registration", "Model", "Service type", "Next service date", "Status"}, true);
            throughField = new JTextField(10);
            throughField.setText(LocalDate.now().plusDays(7).toString());
            addEnterReload(throughField);
            installFilters();
        }

        @Override
        protected JPanel buildFilterBar() {
            JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
            Theme.applyPadding(filters);
            filters.add(new JLabel("Due on or before (yyyy-MM-dd)"));
            filters.add(throughField);
            return filters;
        }

        @Override
        protected ReportJob createJob(final int pageNumber, final int pageSize) {
            final LocalDate through = parseDate(throughField.getText(), "due through");
            if (through == null) {
                throw new IllegalArgumentException("Enter a due-through date.");
            }
            return new ReportJob() {
                @Override
                public ReportRows load() throws LfrlmsException {
                    long count = reportService.countMaintenanceDue(through);
                    ArrayList<HashMap<String, Object>> data =
                            reportService.maintenanceDue(through, pageNumber, pageSize);
                    return new ReportRows(mapRows(data, new String[] {"maintenance_id",
                        "vehicle_id", "registration_no", "model", "service_type",
                        "next_service_date", "status"}), count);
                }
            };
        }
    }

    private class CustomerHistoryReport extends ReportTablePanel {
        private final JTextField customerIdField;

        private CustomerHistoryReport() {
            super("R-08 Customer transaction history", new String[] {"Type", "Reference ID",
                "Transaction date", "Registration", "Status", "Amount"}, true);
            customerIdField = new JTextField(10);
            addEnterReload(customerIdField);
            installFilters();
        }

        @Override
        protected boolean hasRequiredInput() {
            return !customerIdField.getText().trim().isEmpty();
        }

        @Override
        protected String getMissingInputHint() {
            return "Enter a customer ID and press Enter or Refresh.";
        }

        @Override
        protected JPanel buildFilterBar() {
            JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
            Theme.applyPadding(filters);
            filters.add(new JLabel("Customer ID"));
            filters.add(customerIdField);
            return filters;
        }

        @Override
        protected ReportJob createJob(final int pageNumber, final int pageSize) {
            final int customerId;
            try {
                customerId = Integer.parseInt(customerIdField.getText().trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Enter a valid customer ID.");
            }
            if (customerId <= 0) {
                throw new IllegalArgumentException("Enter a valid customer ID.");
            }
            return new ReportJob() {
                @Override
                public ReportRows load() throws LfrlmsException {
                    long count = reportService.countCustomerTransactionHistory(customerId);
                    ArrayList<HashMap<String, Object>> data =
                            reportService.customerTransactionHistory(customerId,
                                    pageNumber, pageSize);
                    return new ReportRows(mapRows(data, new String[] {"transaction_type",
                        "reference_id", "transaction_date", "registration_no", "status",
                        "amount"}), count);
                }
            };
        }
    }

    private class RentalStatusReport extends ReportTablePanel {
        private RentalStatusReport() {
            super("R-09 Rental status summary", new String[] {"Status", "Rental count"}, false);
            installFilters();
        }

        @Override
        protected JPanel buildFilterBar() {
            return infoBar("Counts for Confirmed, Active, Completed and Cancelled rentals");
        }

        @Override
        protected ReportJob createJob(int pageNumber, int pageSize) {
            return new ReportJob() {
                @Override
                public ReportRows load() throws LfrlmsException {
                    ArrayList<HashMap<String, Object>> data =
                            reportService.rentalStatusSummary();
                    HashMap<String, Object> counts = new HashMap<String, Object>();
                    for (HashMap<String, Object> row : data) {
                        counts.put(String.valueOf(row.get("status")), row.get("rental_count"));
                    }
                    Vector<Vector<Object>> rows = new Vector<Vector<Object>>();
                    String[] statuses = new String[] {Constants.RENTAL_CONFIRMED,
                        Constants.RENTAL_ACTIVE, Constants.RENTAL_COMPLETED,
                        Constants.RENTAL_CANCELLED};
                    for (String status : statuses) {
                        Object count = counts.get(status);
                        rows.add(vector(status, count == null ? Integer.valueOf(0) : count));
                    }
                    return new ReportRows(rows, rows.size());
                }
            };
        }
    }

    private JPanel infoBar(String text) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        Theme.applyPadding(panel);
        panel.add(new JLabel(text));
        return panel;
    }

    private Vector<Vector<Object>> mapRows(ArrayList<HashMap<String, Object>> data,
            String[] keys) {
        Vector<Vector<Object>> rows = new Vector<Vector<Object>>();
        for (HashMap<String, Object> row : data) {
            rows.add(vector(row, keys));
        }
        return rows;
    }

    private Vector<Object> vector(HashMap<String, Object> row, String... keys) {
        Vector<Object> values = new Vector<Object>();
        for (String key : keys) {
            values.add(row.get(key));
        }
        return values;
    }

    private Vector<Object> vector(Object... values) {
        Vector<Object> row = new Vector<Object>();
        for (Object value : values) {
            row.add(value);
        }
        return row;
    }

    private Integer selectedId(JComboBox<LookupChoice> box) {
        LookupChoice choice = (LookupChoice) box.getSelectedItem();
        return choice == null || choice.id <= 0 ? null : Integer.valueOf(choice.id);
    }

    private LocalDateTime parseDateTime(String text, String fieldName) {
        try {
            String value = text == null ? "" : text.trim();
            if (value.isEmpty()) {
                return null;
            }
            return DateUtil.parseDateTime(value.replace(' ', 'T'));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Enter " + fieldName
                    + " as yyyy-MM-dd HH:mm.");
        }
    }

    private LocalDate parseDate(String text, String fieldName) {
        try {
            String value = text == null ? "" : text.trim();
            if (value.isEmpty()) {
                return null;
            }
            return DateUtil.parseDate(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Enter " + fieldName
                    + " as yyyy-MM-dd.");
        }
    }

    private interface ReportJob {
        ReportRows load() throws Exception;
    }

    private static class ReportRows {
        private final Vector<Vector<Object>> rows;
        private final long totalRows;

        private ReportRows(Vector<Vector<Object>> rows, long totalRows) {
            this.rows = rows;
            this.totalRows = totalRows;
        }
    }

    private static class LookupData {
        private final ArrayList<Branch> branches;
        private final ArrayList<VehicleCategory> categories;

        private LookupData(ArrayList<Branch> branches,
                ArrayList<VehicleCategory> categories) {
            this.branches = branches;
            this.categories = categories;
        }
    }

    private static class LookupChoice {
        private final int id;
        private final String label;

        private LookupChoice(int id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
