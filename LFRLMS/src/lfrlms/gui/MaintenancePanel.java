package lfrlms.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Vector;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import lfrlms.exception.LfrlmsException;
import lfrlms.model.Maintenance;
import lfrlms.model.Rental;
import lfrlms.service.MaintenanceService;
import lfrlms.service.VehicleService;
import lfrlms.util.Constants;
import lfrlms.util.DateUtil;

/**
 * Purpose: Shows service records and opens or completes vehicle maintenance.
 * Concept: Paged Swing tables, status rendering and background service calls.
 * Layer: GUI (presentation).
 * Rubric: GUI, inner/anonymous classes.
 */
public class MaintenancePanel extends JPanel {
    private final MaintenanceService maintenanceService;
    private final VehicleService vehicleService;
    private final BackgroundTask<SearchData> searchTask;
    private final BackgroundTask<ArrayList<Rental>> openTask;
    private final BackgroundTask<Integer> completeTask;
    private final JTextField textField;
    private final JComboBox<String> statusBox;
    private final JTextField fromField;
    private final JTextField toField;
    private final SharedTableModel tableModel;
    private final JTable table;
    private final PaginationBar pagination;
    private final JLabel messageLabel;
    private final javax.swing.Timer searchTimer;
    private ArrayList<Maintenance> visibleRecords;
    private String sortKey;
    private boolean ascending;
    private JButton completeButton;

    public MaintenancePanel() {
        this(new MaintenanceService(), new VehicleService());
    }

    public MaintenancePanel(MaintenanceService maintenanceService,
            VehicleService vehicleService) {
        this.maintenanceService = maintenanceService;
        this.vehicleService = vehicleService;
        searchTask = new BackgroundTask<SearchData>();
        openTask = new BackgroundTask<ArrayList<Rental>>();
        completeTask = new BackgroundTask<Integer>();
        textField = new JTextField(18);
        statusBox = new JComboBox<String>(new String[] {"Any status",
            Constants.MAINTENANCE_IN_PROGRESS, Constants.MAINTENANCE_COMPLETE});
        fromField = new JTextField(12);
        toField = new JTextField(12);
        fromField.setToolTipText("Service date from: yyyy-MM-dd");
        toField.setToolTipText("Service date to: yyyy-MM-dd");
        tableModel = new SharedTableModel(new String[] {"ID", "Vehicle ID", "Service date",
            "Type", "Description", "Cost", "Next service", "Completed", "Status"});
        table = SharedTableModel.createTable(tableModel);
        pagination = new PaginationBar();
        messageLabel = new JLabel("Loading maintenance records...");
        searchTimer = new javax.swing.Timer(300, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                pagination.setPageNumber(1);
                searchRecords();
            }
        });
        searchTimer.setRepeats(false);
        visibleRecords = new ArrayList<Maintenance>();
        sortKey = "serviceDate";
        ascending = false;
        buildPanel();
        installListeners();
        searchRecords();
    }

    private void buildPanel() {
        setLayout(new BorderLayout(6, 6));
        Theme.applyPadding(this);
        add(buildFilters(), BorderLayout.NORTH);
        table.setFillsViewportHeight(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
    }

    private JPanel buildFilters() {
        JPanel filters = new JPanel(new GridBagLayout());
        Theme.applyPadding(filters);
        addFilter(filters, 0, 0, "Vehicle / service", textField);
        addFilter(filters, 0, 1, "Status", statusBox);
        addFilter(filters, 0, 2, "From", fromField);
        addFilter(filters, 0, 3, "To", toField);
        return filters;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton openButton = new JButton("Open Maintenance");
        completeButton = new JButton("Complete");
        JButton refreshButton = new JButton("Refresh");
        JButton exportButton = new JButton("Export page CSV");
        completeButton.setEnabled(false);
        actions.add(openButton);
        actions.add(completeButton);
        actions.add(refreshButton);
        actions.add(exportButton);
        openButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                openMaintenance();
            }
        });
        completeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                completeMaintenance();
            }
        });
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                searchRecords();
            }
        });
        exportButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                CsvExport.exportCurrentPage(MaintenancePanel.this, table,
                        "maintenance.csv");
            }
        });
        JPanel lower = new JPanel(new BorderLayout());
        lower.add(messageLabel, BorderLayout.WEST);
        lower.add(pagination, BorderLayout.EAST);
        pagination.setPageChangeListener(new PaginationBar.PageChangeListener() {
            @Override
            public void pageChanged(int pageNumber, int pageSize) {
                searchRecords();
            }
        });
        footer.add(actions, BorderLayout.NORTH);
        footer.add(lower, BorderLayout.SOUTH);
        return footer;
    }

    private void installListeners() {
        attachSearchListener(textField);
        attachSearchListener(fromField);
        attachSearchListener(toField);
        statusBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                scheduleSearch();
            }
        });
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent event) {
                if (!event.getValueIsAdjusting()) {
                    Maintenance record = selectedRecord();
                    completeButton.setEnabled(record != null
                            && Constants.MAINTENANCE_IN_PROGRESS.equals(record.getStatus()));
                }
            }
        });
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int viewColumn = table.columnAtPoint(event.getPoint());
                int column = table.convertColumnIndexToModel(viewColumn);
                String key = sortKeyForColumn(column);
                if (key != null) {
                    if (key.equals(sortKey)) {
                        ascending = !ascending;
                    } else {
                        sortKey = key;
                        ascending = true;
                    }
                    searchRecords();
                }
            }
        });
    }

    private void attachSearchListener(JTextField field) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                scheduleSearch();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                scheduleSearch();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                scheduleSearch();
            }
        });
    }

    private void scheduleSearch() {
        searchTimer.restart();
    }

    private void searchRecords() {
        final Filter filter;
        try {
            filter = readFilter();
        } catch (RuntimeException exception) {
            messageLabel.setText(exception.getMessage());
            return;
        }
        final int page = pagination.getPageNumber();
        final int size = pagination.getPageSize();
        final String sort = sortKey;
        final boolean sortAscending = ascending;
        messageLabel.setText("Searching maintenance records...");
        searchTask.execute(this, new BackgroundTask.Work<SearchData>() {
            @Override
            public SearchData run() throws LfrlmsException {
                long count = maintenanceService.countSearchResults(filter.text,
                        filter.status, filter.from, filter.to);
                ArrayList<Maintenance> rows = maintenanceService.search(filter.text,
                        filter.status, filter.from, filter.to,
                        sort, sortAscending, page, size);
                return new SearchData(rows, count);
            }
        }, new BackgroundTask.Completion<SearchData>() {
            @Override
            public void completed(SearchData data, Exception failure) {
                if (failure != null) {
                    messageLabel.setText(" ");
                    GuiSupport.showFailure(MaintenancePanel.this,
                            "Search maintenance", failure, new Runnable() {
                                @Override
                                public void run() {
                                    searchRecords();
                                }
                            });
                    return;
                }
                pagination.setTotalRows(data.count);
                if (pagination.getPageNumber() != page) {
                    searchRecords();
                    return;
                }
                visibleRecords = data.records;
                showRecords(data.records);
                messageLabel.setText(data.count + " maintenance records found.");
            }
        });
    }

    private Filter readFilter() {
        LocalDate from = parseDate(fromField.getText());
        LocalDate to = parseDate(toField.getText());
        if ((from == null) != (to == null) || (from != null && !to.isAfter(from))) {
            throw new IllegalArgumentException("Enter both dates; To must be after From.");
        }
        String status = (String) statusBox.getSelectedItem();
        if (status != null && status.startsWith("Any")) {
            status = null;
        }
        return new Filter(textField.getText().trim(), status, from, to);
    }

    private LocalDate parseDate(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return DateUtil.parseDate(text);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Use yyyy-MM-dd for service date filters.");
        }
    }

    private void showRecords(ArrayList<Maintenance> records) {
        Vector<Vector<Object>> rows = new Vector<Vector<Object>>();
        for (Maintenance record : records) {
            Vector<Object> row = new Vector<Object>();
            row.add(Integer.valueOf(record.getMaintenanceId()));
            row.add(Integer.valueOf(record.getVehicleId()));
            row.add(DateUtil.formatDate(record.getServiceDate()));
            row.add(record.getServiceType());
            row.add(record.getDescription());
            row.add(record.getCost() == null ? "-" : record.getCost().toPlainString());
            row.add(DateUtil.formatDate(record.getNextServiceDate()));
            row.add(DateUtil.formatDate(record.getCompletedDate()));
            row.add(record.getStatus());
            rows.add(row);
        }
        tableModel.replaceRows(rows);
        int viewColumn = table.convertColumnIndexToView(tableModel.findColumn("Status"));
        if (viewColumn >= 0) {
            table.getColumnModel().getColumn(viewColumn)
                    .setCellRenderer(new StatusColourRenderer());
        }
    }

    private void openMaintenance() {
        final Maintenance value = MaintenanceFormDialog.showDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this), vehicleService);
        if (value == null) {
            return;
        }
        openTask.execute(this, new BackgroundTask.Work<ArrayList<Rental>>() {
            @Override
            public ArrayList<Rental> run() throws LfrlmsException {
                return maintenanceService.openMaintenance(value);
            }
        }, new BackgroundTask.Completion<ArrayList<Rental>>() {
            @Override
            public void completed(ArrayList<Rental> warnings, Exception failure) {
                if (failure != null) {
                    GuiSupport.showFailure(MaintenancePanel.this, "Open maintenance",
                            failure, null);
                    return;
                }
                if (warnings.isEmpty()) {
                    JOptionPane.showMessageDialog(MaintenancePanel.this,
                            "Maintenance opened.", "Maintenance", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(MaintenancePanel.this,
                            confirmedRentalWarning(warnings), "Future reservations",
                            JOptionPane.WARNING_MESSAGE);
                }
                searchRecords();
            }
        });
    }

    private String confirmedRentalWarning(ArrayList<Rental> warnings) {
        StringBuilder message = new StringBuilder();
        message.append("Maintenance opened. These future Confirmed rentals use this vehicle:\n");
        for (Rental rental : warnings) {
            message.append("Rental #").append(rental.getRentalId()).append(" at ")
                    .append(DateUtil.formatDateTime(rental.getScheduledStartDatetime()))
                    .append('\n');
        }
        return message.toString();
    }

    private void completeMaintenance() {
        final Maintenance record = selectedRecord();
        if (record == null) {
            return;
        }
        String enteredDate = JOptionPane.showInputDialog(this,
                "Completion date (yyyy-MM-dd):", DateUtil.formatDate(LocalDate.now()));
        if (enteredDate == null) {
            return;
        }
        final LocalDate completedDate;
        try {
            completedDate = DateUtil.parseDate(enteredDate.trim());
            if (completedDate == null) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, "Use the yyyy-MM-dd date format.",
                    "Invalid date", JOptionPane.ERROR_MESSAGE);
            return;
        }
        completeTask.execute(this, new BackgroundTask.Work<Integer>() {
            @Override
            public Integer run() throws LfrlmsException {
                maintenanceService.completeMaintenance(record.getMaintenanceId(), completedDate);
                return Integer.valueOf(record.getMaintenanceId());
            }
        }, new BackgroundTask.Completion<Integer>() {
            @Override
            public void completed(Integer id, Exception failure) {
                if (failure != null) {
                    GuiSupport.showFailure(MaintenancePanel.this,
                            "Complete maintenance", failure, null);
                    return;
                }
                messageLabel.setText("Maintenance #" + id + " completed.");
                searchRecords();
            }
        });
    }

    private Maintenance selectedRecord() {
        int row = table.getSelectedRow();
        return row < 0 || row >= visibleRecords.size() ? null : visibleRecords.get(row);
    }

    private String sortKeyForColumn(int column) {
        if (column == 0) {
            return "id";
        }
        if (column == 2) {
            return "serviceDate";
        }
        return column == 8 ? "status" : null;
    }

    private void addFilter(JPanel panel, int row, int column,
            String title, java.awt.Component component) {
        GridBagConstraints label = new GridBagConstraints();
        label.gridx = column * 2;
        label.gridy = row;
        label.anchor = GridBagConstraints.LINE_END;
        label.insets = new Insets(3, 5, 3, 3);
        panel.add(new JLabel(title + ":"), label);
        GridBagConstraints control = new GridBagConstraints();
        control.gridx = column * 2 + 1;
        control.gridy = row;
        control.weightx = 1;
        control.fill = GridBagConstraints.HORIZONTAL;
        control.insets = new Insets(3, 2, 3, 5);
        panel.add(component, control);
    }

    private static class SearchData {
        private final ArrayList<Maintenance> records;
        private final long count;

        private SearchData(ArrayList<Maintenance> records, long count) {
            this.records = records;
            this.count = count;
        }
    }

    private static class Filter {
        private final String text;
        private final String status;
        private final LocalDate from;
        private final LocalDate to;

        private Filter(String text, String status, LocalDate from, LocalDate to) {
            this.text = text;
            this.status = status;
            this.from = from;
            this.to = to;
        }
    }
}
