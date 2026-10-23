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
import lfrlms.model.Payment;
import lfrlms.service.PaymentService;
import lfrlms.util.Constants;
import lfrlms.util.DateUtil;

/**
 * Purpose: Searches paginated payments and records or refunds payments through the service.
 * Concept: Swing filters, status rendering and background service calls.
 * Layer: GUI (presentation).
 * Rubric: GUI, inner/anonymous classes.
 */
public class PaymentsPanel extends JPanel {
    private final PaymentService paymentService;
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
    private ArrayList<Payment> visiblePayments;
    private String sortKey;
    private boolean ascending;
    private JButton refundButton;

    public PaymentsPanel() {
        this(new PaymentService());
    }

    public PaymentsPanel(PaymentService paymentService) {
        this.paymentService = paymentService;
        searchTask = new BackgroundTask<SearchData>();
        writeTask = new BackgroundTask<Integer>();
        textField = new JTextField(20);
        statusBox = new JComboBox<String>(new String[] {"Any status",
            Constants.PAYMENT_SUCCESS, Constants.PAYMENT_PENDING,
            Constants.PAYMENT_FAILED, Constants.PAYMENT_REFUNDED});
        fromField = new JTextField(16);
        toField = new JTextField(16);
        fromField.setToolTipText("Filter start: yyyy-MM-dd HH:mm");
        toField.setToolTipText("Filter end: yyyy-MM-dd HH:mm");
        outstandingBox = new JCheckBox("Outstanding only");
        tableModel = new SharedTableModel(new String[] {"ID", "Rental ID", "Lease ID",
            "Amount", "Method", "Payment date", "Status"});
        table = SharedTableModel.createTable(tableModel);
        pagination = new PaginationBar();
        messageLabel = new JLabel("Loading payments...");
        searchTimer = new javax.swing.Timer(300, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                pagination.setPageNumber(1);
                searchPayments();
            }
        });
        searchTimer.setRepeats(false);
        visiblePayments = new ArrayList<Payment>();
        sortKey = "paymentDate";
        ascending = false;
        buildPanel();
        installListeners();
        searchPayments();
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
        JButton addButton = new JButton("Record Payment");
        refundButton = new JButton("Refund");
        JButton refreshButton = new JButton("Refresh");
        JButton exportButton = new JButton("Export page CSV");
        refundButton.setEnabled(false);
        actions.add(addButton);
        actions.add(refundButton);
        actions.add(refreshButton);
        actions.add(exportButton);
        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                boolean saved = PaymentFormDialog.showDialog(
                        javax.swing.SwingUtilities.getWindowAncestor(PaymentsPanel.this),
                        paymentService);
                if (saved) {
                    searchPayments();
                }
            }
        });
        refundButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                refundPayment();
            }
        });
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                searchPayments();
            }
        });
        exportButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                CsvExport.exportCurrentPage(PaymentsPanel.this, table, "payments.csv");
            }
        });
        JPanel lower = new JPanel(new BorderLayout());
        lower.add(messageLabel, BorderLayout.WEST);
        lower.add(pagination, BorderLayout.EAST);
        pagination.setPageChangeListener(new PaginationBar.PageChangeListener() {
            @Override
            public void pageChanged(int pageNumber, int pageSize) {
                searchPayments();
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
                    Payment payment = selectedPayment();
                    refundButton.setEnabled(payment != null
                            && !Constants.PAYMENT_REFUNDED.equals(payment.getStatus()));
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
                    searchPayments();
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

    private void searchPayments() {
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
        messageLabel.setText("Searching payments...");
        searchTask.execute(this, new BackgroundTask.Work<SearchData>() {
            @Override
            public SearchData run() throws LfrlmsException {
                long count = paymentService.countSearchResults(filter.text, filter.status,
                        filter.from, filter.to, filter.outstandingOnly);
                ArrayList<Payment> rows = paymentService.search(filter.text, filter.status,
                        filter.from, filter.to, filter.outstandingOnly,
                        sort, sortAscending, page, size);
                return new SearchData(rows, count);
            }
        }, new BackgroundTask.Completion<SearchData>() {
            @Override
            public void completed(SearchData data, Exception failure) {
                if (failure != null) {
                    messageLabel.setText(" ");
                    GuiSupport.showFailure(PaymentsPanel.this, "Search payments", failure,
                            new Runnable() {
                                @Override
                                public void run() {
                                    searchPayments();
                                }
                            });
                    return;
                }
                pagination.setTotalRows(data.count);
                if (pagination.getPageNumber() != page) {
                    searchPayments();
                    return;
                }
                visiblePayments = data.payments;
                showPayments(data.payments);
                messageLabel.setText(data.count + " payments found.");
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
                    "Use yyyy-MM-dd HH:mm for payment date filters.");
        }
    }

    private void showPayments(ArrayList<Payment> payments) {
        Vector<Vector<Object>> rows = new Vector<Vector<Object>>();
        for (Payment payment : payments) {
            Vector<Object> row = new Vector<Object>();
            row.add(Integer.valueOf(payment.getPaymentId()));
            row.add(payment.getRentalId() == null ? "-" : payment.getRentalId());
            row.add(payment.getLeaseId() == null ? "-" : payment.getLeaseId());
            row.add(payment.getAmount().toPlainString());
            row.add(payment.getMethod());
            row.add(DateUtil.formatDateTime(payment.getPaymentDate()));
            row.add(payment.getStatus());
            rows.add(row);
        }
        tableModel.replaceRows(rows);
        int viewColumn = table.convertColumnIndexToView(tableModel.findColumn("Status"));
        if (viewColumn >= 0) {
            table.getColumnModel().getColumn(viewColumn)
                    .setCellRenderer(new StatusColourRenderer());
        }
    }

    private void refundPayment() {
        final Payment payment = selectedPayment();
        if (payment == null || JOptionPane.showConfirmDialog(this,
                "Refund payment #" + payment.getPaymentId() + "?",
                "Confirm refund", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        writeTask.execute(this, new BackgroundTask.Work<Integer>() {
            @Override
            public Integer run() throws LfrlmsException {
                paymentService.refund(payment.getPaymentId());
                return Integer.valueOf(payment.getPaymentId());
            }
        }, new BackgroundTask.Completion<Integer>() {
            @Override
            public void completed(Integer paymentId, Exception failure) {
                if (failure != null) {
                    GuiSupport.showFailure(PaymentsPanel.this, "Refund payment", failure, null);
                    return;
                }
                messageLabel.setText("Payment #" + paymentId + " refunded.");
                searchPayments();
            }
        });
    }

    private Payment selectedPayment() {
        int row = table.getSelectedRow();
        return row < 0 || row >= visiblePayments.size() ? null : visiblePayments.get(row);
    }

    private String sortKeyForColumn(int column) {
        if (column == 0) {
            return "id";
        }
        if (column == 3) {
            return "amount";
        }
        if (column == 5) {
            return "paymentDate";
        }
        return column == 6 ? "status" : null;
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
        private final ArrayList<Payment> payments;
        private final long count;

        private SearchData(ArrayList<Payment> payments, long count) {
            this.payments = payments;
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
