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
import javax.swing.JCheckBox;
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
import lfrlms.model.Lease;
import lfrlms.service.LeaseService;
import lfrlms.util.Constants;
import lfrlms.util.DateUtil;

/**
 * Purpose: Schedules leases and manages their start and final status actions.
 * Concept: Paged Swing tables, status rendering and background service calls.
 * Layer: GUI (presentation).
 * Rubric: GUI, inner/anonymous classes.
 */
public class LeasesPanel extends JPanel {
    private final LeaseService leaseService;
    private final BackgroundTask<SearchData> searchTask;
    private final BackgroundTask<Integer> writeTask;
    private final JTextField textField;
    private final JComboBox<String> statusBox;
    private final JTextField fromField;
    private final JTextField toField;
    private final JCheckBox outstandingBox;
    private final SharedTableModel tableModel;
    private final JTable table;
    private final PaginationBar pagination;
    private final JLabel messageLabel;
    private final javax.swing.Timer searchTimer;
    private ArrayList<Lease> visibleLeases;
    private String sortKey;
    private boolean ascending;
    private JButton startButton;
    private JButton completeButton;
    private JButton terminateButton;

    public LeasesPanel() {
        this(new LeaseService());
    }

    public LeasesPanel(LeaseService leaseService) {
        this.leaseService = leaseService;
        searchTask = new BackgroundTask<SearchData>();
        writeTask = new BackgroundTask<Integer>();
        textField = new JTextField(20);
        statusBox = new JComboBox<String>(new String[] {"Any status",
            Constants.LEASE_SCHEDULED, Constants.LEASE_ACTIVE,
            Constants.LEASE_COMPLETED, Constants.LEASE_TERMINATED});
        fromField = new JTextField(12);
        toField = new JTextField(12);
        fromField.setToolTipText("Filter start date: yyyy-MM-dd");
        toField.setToolTipText("Filter end date: yyyy-MM-dd");
        outstandingBox = new JCheckBox("Outstanding only");
        tableModel = new SharedTableModel(new String[] {"ID", "Customer ID", "Vehicle ID",
            "Start date", "End date", "Monthly charge", "Deposit", "Status"});
        table = SharedTableModel.createTable(tableModel);
        pagination = new PaginationBar();
        messageLabel = new JLabel("Loading leases...");
        searchTimer = new javax.swing.Timer(300, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                pagination.setPageNumber(1);
                searchLeases();
            }
        });
        searchTimer.setRepeats(false);
        visibleLeases = new ArrayList<Lease>();
        sortKey = "startDate";
        ascending = true;
        buildPanel();
        installListeners();
        searchLeases();
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
        addFilter(filters, 0, 0, "Customer / vehicle", textField);
        addFilter(filters, 0, 1, "Status", statusBox);
        addFilter(filters, 0, 2, "From", fromField);
        addFilter(filters, 0, 3, "To", toField);
        GridBagConstraints constraint = new GridBagConstraints();
        constraint.gridx = 8;
        constraint.gridy = 0;
        constraint.insets = new Insets(3, 4, 3, 8);
        filters.add(outstandingBox, constraint);
        return filters;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton scheduleButton = new JButton("Schedule Lease");
        startButton = new JButton("Start");
        completeButton = new JButton("Complete");
        terminateButton = new JButton("Terminate");
        JButton refreshButton = new JButton("Refresh");
        JButton exportButton = new JButton("Export page CSV");
        actions.add(scheduleButton);
        actions.add(startButton);
        actions.add(completeButton);
        actions.add(terminateButton);
        actions.add(refreshButton);
        actions.add(exportButton);
        updateActionButtons();
        scheduleButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                scheduleLease();
            }
        });
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                performTransition(Constants.LEASE_ACTIVE);
            }
        });
        completeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                performTransition(Constants.LEASE_COMPLETED);
            }
        });
        terminateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                performTransition(Constants.LEASE_TERMINATED);
            }
        });
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                searchLeases();
            }
        });
        exportButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                CsvExport.exportCurrentPage(LeasesPanel.this, table, "leases.csv");
            }
        });
        JPanel lower = new JPanel(new BorderLayout());
        lower.add(messageLabel, BorderLayout.WEST);
        lower.add(pagination, BorderLayout.EAST);
        pagination.setPageChangeListener(new PaginationBar.PageChangeListener() {
            @Override
            public void pageChanged(int pageNumber, int pageSize) {
                searchLeases();
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
        outstandingBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                scheduleSearch();
            }
        });
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent event) {
                if (!event.getValueIsAdjusting()) {
                    updateActionButtons();
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
                    searchLeases();
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

    private void searchLeases() {
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
        messageLabel.setText("Searching leases...");
        searchTask.execute(this, new BackgroundTask.Work<SearchData>() {
            @Override
            public SearchData run() throws LfrlmsException {
                long count = leaseService.countSearchResults(filter.text, filter.status,
                        filter.from, filter.to, filter.outstandingOnly);
                ArrayList<Lease> rows = leaseService.search(filter.text, filter.status,
                        filter.from, filter.to, filter.outstandingOnly,
                        sort, sortAscending, page, size);
                return new SearchData(rows, count);
            }
        }, new BackgroundTask.Completion<SearchData>() {
            @Override
            public void completed(SearchData data, Exception failure) {
                if (failure != null) {
                    messageLabel.setText(" ");
                    GuiSupport.showFailure(LeasesPanel.this, "Search leases", failure,
                            new Runnable() {
                                @Override
                                public void run() {
                                    searchLeases();
                                }
                            });
                    return;
                }
                pagination.setTotalRows(data.count);
                if (pagination.getPageNumber() != page) {
                    searchLeases();
                    return;
                }
                visibleLeases = data.leases;
                showLeases(data.leases);
                messageLabel.setText(data.count + " leases found.");
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
        return new Filter(textField.getText().trim(), status, from, to,
                outstandingBox.isSelected());
    }

    private LocalDate parseDate(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return DateUtil.parseDate(text);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("Use yyyy-MM-dd for lease date filters.");
        }
    }

    private void showLeases(ArrayList<Lease> leases) {
        Vector<Vector<Object>> rows = new Vector<Vector<Object>>();
        for (Lease lease : leases) {
            Vector<Object> row = new Vector<Object>();
            row.add(Integer.valueOf(lease.getLeaseId()));
            row.add(Integer.valueOf(lease.getCustomerId()));
            row.add(Integer.valueOf(lease.getVehicleId()));
            row.add(DateUtil.formatDate(lease.getStartDate()));
            row.add(DateUtil.formatDate(lease.getEndDate()));
            row.add(lease.getMonthlyCharge() == null ? "-"
                    : lease.getMonthlyCharge().toPlainString());
            row.add(lease.getDeposit() == null ? "-" : lease.getDeposit().toPlainString());
            row.add(lease.getStatus());
            rows.add(row);
        }
        tableModel.replaceRows(rows);
        int viewColumn = table.convertColumnIndexToView(tableModel.findColumn("Status"));
        if (viewColumn >= 0) {
            table.getColumnModel().getColumn(viewColumn)
                    .setCellRenderer(new StatusColourRenderer());
        }
        updateActionButtons();
    }

    private void scheduleLease() {
        boolean created = ContractFormDialog.showDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this), false, null);
        if (created) {
            searchLeases();
        }
    }

    private void performTransition(final String targetStatus) {
        final Lease lease = selectedLease();
        if (lease == null) {
            return;
        }
        String action = Constants.LEASE_ACTIVE.equals(targetStatus) ? "Start" :
                (Constants.LEASE_COMPLETED.equals(targetStatus) ? "Complete" : "Terminate");
        if (JOptionPane.showConfirmDialog(this,
                action + " lease #" + lease.getLeaseId() + "?", "Confirm lease action",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        writeTask.execute(this, new BackgroundTask.Work<Integer>() {
            @Override
            public Integer run() throws LfrlmsException {
                if (Constants.LEASE_ACTIVE.equals(targetStatus)) {
                    leaseService.startLease(lease.getLeaseId());
                } else {
                    leaseService.finishLease(lease.getLeaseId(), targetStatus);
                }
                return Integer.valueOf(lease.getLeaseId());
            }
        }, new BackgroundTask.Completion<Integer>() {
            @Override
            public void completed(Integer leaseId, Exception failure) {
                if (failure != null) {
                    GuiSupport.showFailure(LeasesPanel.this, "Update lease", failure, null);
                    return;
                }
                messageLabel.setText("Lease #" + leaseId + " updated.");
                searchLeases();
            }
        });
    }

    private void updateActionButtons() {
        Lease lease = selectedLease();
        boolean scheduled = lease != null
                && Constants.LEASE_SCHEDULED.equals(lease.getStatus());
        boolean active = lease != null && Constants.LEASE_ACTIVE.equals(lease.getStatus());
        if (startButton != null) {
            startButton.setEnabled(scheduled);
            completeButton.setEnabled(active);
            terminateButton.setEnabled(scheduled || active);
        }
    }

    private Lease selectedLease() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= visibleLeases.size()) {
            return null;
        }
        return visibleLeases.get(row);
    }

    private String sortKeyForColumn(int column) {
        if (column == 0) {
            return "id";
        }
        if (column == 3) {
            return "startDate";
        }
        if (column == 4) {
            return "endDate";
        }
        return column == 7 ? "status" : null;
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
        private final ArrayList<Lease> leases;
        private final long count;

        private SearchData(ArrayList<Lease> leases, long count) {
            this.leases = leases;
            this.count = count;
        }
    }

    private static class Filter {
        private final String text;
        private final String status;
        private final LocalDate from;
        private final LocalDate to;
        private final boolean outstandingOnly;

        private Filter(String text, String status, LocalDate from,
                LocalDate to, boolean outstandingOnly) {
            this.text = text;
            this.status = status;
            this.from = from;
            this.to = to;
            this.outstandingOnly = outstandingOnly;
        }
    }
}
