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
import java.time.LocalDateTime;
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
import lfrlms.model.Rental;
import lfrlms.service.AvailabilityService;
import lfrlms.service.BillingService;
import lfrlms.service.CustomerService;
import lfrlms.service.DriverService;
import lfrlms.service.LeaseService;
import lfrlms.service.RentalService;
import lfrlms.util.Constants;
import lfrlms.util.DateUtil;

/**
 * Purpose: Filters rentals and runs reservation, pickup, cancellation and return actions.
 * Concept: Paged Swing tables, listeners and background service calls.
 * Layer: GUI (presentation).
 * Rubric: GUI, inner/anonymous classes.
 */
public class RentalsPanel extends JPanel {
    private final RentalService rentalService;
    private final BackgroundTask<SearchData> searchTask;
    private final BackgroundTask<Integer> writeTask;
    private final BackgroundTask<Rental> completeTask;
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
    private ArrayList<Rental> visibleRentals;
    private String sortKey;
    private boolean ascending;
    private JButton startButton;
    private JButton cancelButton;
    private JButton returnButton;

    public RentalsPanel() {
        this(new RentalService());
    }

    public RentalsPanel(RentalService rentalService) {
        this.rentalService = rentalService;
        searchTask = new BackgroundTask<SearchData>();
        writeTask = new BackgroundTask<Integer>();
        completeTask = new BackgroundTask<Rental>();
        textField = new JTextField(20);
        statusBox = new JComboBox<String>(new String[] {"Any status",
            Constants.RENTAL_CONFIRMED, Constants.RENTAL_ACTIVE,
            Constants.RENTAL_COMPLETED, Constants.RENTAL_CANCELLED});
        fromField = new JTextField(16);
        toField = new JTextField(16);
        fromField.setToolTipText("Filter start: yyyy-MM-dd HH:mm");
        toField.setToolTipText("Filter end: yyyy-MM-dd HH:mm");
        outstandingBox = new JCheckBox("Outstanding only");
        tableModel = new SharedTableModel(new String[] {"ID", "Customer ID", "Vehicle ID",
            "Driver ID", "Scheduled start", "Expected return", "Actual pickup",
            "Actual return", "Final charges", "Status"});
        table = SharedTableModel.createTable(tableModel);
        pagination = new PaginationBar();
        messageLabel = new JLabel("Loading rentals...");
        searchTimer = new javax.swing.Timer(300, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                pagination.setPageNumber(1);
                searchRentals();
            }
        });
        searchTimer.setRepeats(false);
        visibleRentals = new ArrayList<Rental>();
        sortKey = "scheduledStart";
        ascending = true;
        buildPanel();
        installListeners();
        searchRentals();
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
        JButton newButton = new JButton("New Rental");
        startButton = new JButton("Start");
        cancelButton = new JButton("Cancel Reservation");
        returnButton = new JButton("Return");
        JButton refreshButton = new JButton("Refresh");
        JButton exportButton = new JButton("Export page CSV");
        actions.add(newButton);
        actions.add(startButton);
        actions.add(cancelButton);
        actions.add(returnButton);
        actions.add(refreshButton);
        actions.add(exportButton);
        setActionButtons(false, false, false);
        newButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                showNewRental(null);
            }
        });
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                startRental();
            }
        });
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                cancelRental();
            }
        });
        returnButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                returnRental();
            }
        });
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                searchRentals();
            }
        });
        exportButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                CsvExport.exportCurrentPage(RentalsPanel.this, table, "rentals.csv");
            }
        });
        JPanel lower = new JPanel(new BorderLayout());
        lower.add(messageLabel, BorderLayout.WEST);
        lower.add(pagination, BorderLayout.EAST);
        pagination.setPageChangeListener(new PaginationBar.PageChangeListener() {
            @Override
            public void pageChanged(int pageNumber, int pageSize) {
                searchRentals();
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
                    searchRentals();
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

    private void searchRentals() {
        final Filter filter;
        try {
            filter = readFilter();
        } catch (RuntimeException exception) {
            messageLabel.setText(exception.getMessage());
            return;
        }
        final int page = pagination.getPageNumber();
        final int pageSize = pagination.getPageSize();
        final String sort = sortKey;
        final boolean sortAscending = ascending;
        messageLabel.setText("Searching rentals...");
        searchTask.execute(this, new BackgroundTask.Work<SearchData>() {
            @Override
            public SearchData run() throws LfrlmsException {
                long count = rentalService.countSearchResults(filter.text, filter.status,
                        filter.from, filter.to, filter.outstandingOnly);
                ArrayList<Rental> rows = rentalService.search(filter.text, filter.status,
                        filter.from, filter.to, filter.outstandingOnly,
                        sort, sortAscending, page, pageSize);
                return new SearchData(rows, count);
            }
        }, new BackgroundTask.Completion<SearchData>() {
            @Override
            public void completed(SearchData data, Exception failure) {
                if (failure != null) {
                    messageLabel.setText(" ");
                    GuiSupport.showFailure(RentalsPanel.this, "Search rentals", failure,
                            new Runnable() {
                                @Override
                                public void run() {
                                    searchRentals();
                                }
                            });
                    return;
                }
                pagination.setTotalRows(data.count);
                if (pagination.getPageNumber() != page) {
                    searchRentals();
                    return;
                }
                visibleRentals = data.rentals;
                showRentals(data.rentals);
                messageLabel.setText(data.count + " rentals found.");
            }
        });
    }

    private Filter readFilter() {
        LocalDateTime from = parseDateTime(fromField.getText());
        LocalDateTime to = parseDateTime(toField.getText());
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

    private LocalDateTime parseDateTime(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return DateUtil.parseDateTime(text.replace(' ', 'T'));
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Use yyyy-MM-dd HH:mm for rental date filters.");
        }
    }

    private void showRentals(ArrayList<Rental> rentals) {
        Vector<Vector<Object>> rows = new Vector<Vector<Object>>();
        for (Rental rental : rentals) {
            Vector<Object> row = new Vector<Object>();
            row.add(Integer.valueOf(rental.getRentalId()));
            row.add(Integer.valueOf(rental.getCustomerId()));
            row.add(Integer.valueOf(rental.getVehicleId()));
            row.add(rental.getDriverId() == null ? "-" : rental.getDriverId());
            row.add(DateUtil.formatDateTime(rental.getScheduledStartDatetime()));
            row.add(DateUtil.formatDateTime(rental.getExpectedReturnDatetime()));
            row.add(DateUtil.formatDateTime(rental.getActualPickupDatetime()));
            row.add(DateUtil.formatDateTime(rental.getActualReturnDatetime()));
            row.add(rental.getCharges() == null ? "-" : rental.getCharges().toPlainString());
            row.add(rental.getStatus());
            rows.add(row);
        }
        tableModel.replaceRows(rows);
        applyStatusRenderer();
        updateActionButtons();
    }

    private void applyStatusRenderer() {
        int column = table.convertColumnIndexToView(tableModel.findColumn("Status"));
        if (column >= 0) {
            table.getColumnModel().getColumn(column)
                    .setCellRenderer(new StatusColourRenderer());
        }
    }

    private void startRental() {
        final Rental rental = selectedRental();
        if (rental == null) {
            return;
        }
        RentalActionDialog.RentalAction action = RentalActionDialog.showStartDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this));
        if (action == null) {
            return;
        }
        writeTask.execute(this, new BackgroundTask.Work<Integer>() {
            @Override
            public Integer run() throws LfrlmsException {
                rentalService.startRental(rental.getRentalId(), action.getDateTime(),
                        action.getOdometer(), action.getCondition());
                return Integer.valueOf(rental.getRentalId());
            }
        }, writeCompletion("Start rental"));
    }

    private void cancelRental() {
        final Rental rental = selectedRental();
        if (rental == null || JOptionPane.showConfirmDialog(this,
                "Cancel reservation #" + rental.getRentalId() + "?",
                "Confirm cancellation", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        writeTask.execute(this, new BackgroundTask.Work<Integer>() {
            @Override
            public Integer run() throws LfrlmsException {
                rentalService.cancelRental(rental.getRentalId());
                return Integer.valueOf(rental.getRentalId());
            }
        }, writeCompletion("Cancel reservation"));
    }

    private void returnRental() {
        final Rental rental = selectedRental();
        if (rental == null) {
            return;
        }
        final RentalActionDialog.RentalAction action = RentalActionDialog.showReturnDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this));
        if (action == null) {
            return;
        }
        completeTask.execute(this, new BackgroundTask.Work<Rental>() {
            @Override
            public Rental run() throws LfrlmsException {
                rentalService.completeRental(rental.getRentalId(), action.getDateTime(),
                        action.getOdometer(), action.getCondition());
                return rentalService.findById(rental.getRentalId());
            }
        }, new BackgroundTask.Completion<Rental>() {
            @Override
            public void completed(Rental completed, Exception failure) {
                if (failure != null) {
                    GuiSupport.showFailure(RentalsPanel.this, "Return vehicle", failure,
                            new Runnable() {
                                @Override
                                public void run() {
                                    returnRental();
                                }
                            });
                    return;
                }
                JOptionPane.showMessageDialog(RentalsPanel.this,
                        "Rental completed. Final charge: "
                        + completed.getCharges().toPlainString());
                searchRentals();
            }
        });
    }

    private BackgroundTask.Completion<Integer> writeCompletion(final String action) {
        return new BackgroundTask.Completion<Integer>() {
            @Override
            public void completed(Integer rentalId, Exception failure) {
                if (failure != null) {
                    GuiSupport.showFailure(RentalsPanel.this, action, failure, null);
                    return;
                }
                messageLabel.setText(action + " completed for rental #" + rentalId + ".");
                searchRentals();
            }
        };
    }

    private void showNewRental(Integer vehicleId) {
        boolean created = ContractFormDialog.showDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this), true, vehicleId);
        if (created) {
            searchRentals();
        }
    }

    private void setActionButtons(boolean start, boolean cancel, boolean complete) {
        if (startButton != null) {
            startButton.setEnabled(start);
            cancelButton.setEnabled(cancel);
            returnButton.setEnabled(complete);
        }
    }

    private void updateActionButtons() {
        Rental rental = selectedRental();
        setActionButtons(rental != null
                && Constants.RENTAL_CONFIRMED.equals(rental.getStatus()),
                rental != null && Constants.RENTAL_CONFIRMED.equals(rental.getStatus()),
                rental != null && Constants.RENTAL_ACTIVE.equals(rental.getStatus()));
    }

    private Rental selectedRental() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= visibleRentals.size()) {
            return null;
        }
        return visibleRentals.get(row);
    }

    private String sortKeyForColumn(int column) {
        if (column == 0) {
            return "id";
        }
        if (column == 4) {
            return "scheduledStart";
        }
        if (column == 5) {
            return "expectedReturn";
        }
        return column == 9 ? "status" : null;
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
        private final ArrayList<Rental> rentals;
        private final long count;

        private SearchData(ArrayList<Rental> rentals, long count) {
            this.rentals = rentals;
            this.count = count;
        }
    }

    private static class Filter {
        private final String text;
        private final String status;
        private final LocalDateTime from;
        private final LocalDateTime to;
        private final boolean outstandingOnly;

        private Filter(String text, String status, LocalDateTime from,
                LocalDateTime to, boolean outstandingOnly) {
            this.text = text;
            this.status = status;
            this.from = from;
            this.to = to;
            this.outstandingOnly = outstandingOnly;
        }
    }
}
