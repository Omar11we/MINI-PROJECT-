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
import lfrlms.model.CorporateCustomer;
import lfrlms.model.Customer;
import lfrlms.model.IndividualCustomer;
import lfrlms.service.CustomerService;
import lfrlms.service.ReportService;
import lfrlms.util.Constants;
import lfrlms.util.DateUtil;

/**
 * Purpose: Searches and manages individual and corporate customer records.
 * Concept: Polymorphic models, paged tables and background service calls.
 * Layer: GUI (presentation).
 * Rubric: GUI, inner/anonymous classes.
 */
public class CustomersPanel extends JPanel {
    private static final int DEFAULT_PAGE_SIZE = 10;
    private final CustomerService customerService;
    private final ReportService reportService;
    private final BackgroundTask<SearchData> searchTask;
    private final BackgroundTask<Boolean> deleteCheckTask;
    private final BackgroundTask<Integer> writeTask;
    private final JTextField textField;
    private final JComboBox<String> typeBox;
    private final JComboBox<String> expiryBox;
    private final SharedTableModel tableModel;
    private final JTable table;
    private final PaginationBar pagination;
    private final JLabel messageLabel;
    private final javax.swing.Timer searchTimer;
    private ArrayList<Customer> visibleCustomers;
    private String sortKey;
    private boolean ascending;

    public CustomersPanel() {
        this(new CustomerService(), new ReportService());
    }

    public CustomersPanel(CustomerService customerService, ReportService reportService) {
        this.customerService = customerService;
        this.reportService = reportService;
        searchTask = new BackgroundTask<SearchData>();
        deleteCheckTask = new BackgroundTask<Boolean>();
        writeTask = new BackgroundTask<Integer>();
        textField = new JTextField(24);
        typeBox = new JComboBox<String>(new String[] {
            "Any type", Constants.CUSTOMER_INDIVIDUAL, Constants.CUSTOMER_CORPORATE});
        expiryBox = new JComboBox<String>(new String[] {
            "Any licence", "Expiring within 30 days", "Expiring within 60 days",
            "Expiring within 90 days"});
        tableModel = new SharedTableModel(new String[] {"ID", "Type", "Name / Company",
            "Contact", "Email", "Phone", "Licence / GST", "Licence expiry"});
        table = SharedTableModel.createTable(tableModel);
        pagination = new PaginationBar();
        messageLabel = new JLabel(" ");
        searchTimer = new javax.swing.Timer(300, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                pagination.setPageNumber(1);
                searchCustomers();
            }
        });
        searchTimer.setRepeats(false);
        visibleCustomers = new ArrayList<Customer>();
        sortKey = "lastName";
        ascending = true;
        buildPanel();
        installListeners();
        searchCustomers();
    }

    private void buildPanel() {
        setLayout(new BorderLayout(6, 6));
        Theme.applyPadding(this);
        JPanel filters = new JPanel(new GridBagLayout());
        Theme.applyPadding(filters);
        addFilter(filters, 0, "Search name, phone, email, company or licence", textField);
        addFilter(filters, 1, "Type", typeBox);
        addFilter(filters, 2, "Licence expiry", expiryBox);
        add(filters, BorderLayout.NORTH);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(false);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("Add");
        JButton editButton = new JButton("Edit");
        JButton deleteButton = new JButton("Delete");
        JButton detailsButton = new JButton("Details");
        JButton refreshButton = new JButton("Refresh");
        JButton exportButton = new JButton("Export page CSV");
        actions.add(addButton);
        actions.add(editButton);
        actions.add(deleteButton);
        actions.add(detailsButton);
        actions.add(refreshButton);
        actions.add(exportButton);
        configureSelectionButtons(editButton, deleteButton, detailsButton);
        bindAction(addButton, "add");
        bindAction(editButton, "edit");
        bindAction(deleteButton, "delete");
        bindAction(detailsButton, "details");
        bindAction(refreshButton, "refresh");
        bindAction(exportButton, "export");
        pagination.setPageChangeListener(new PaginationBar.PageChangeListener() {
            @Override
            public void pageChanged(int pageNumber, int pageSize) {
                searchCustomers();
            }
        });
        JPanel lower = new JPanel(new BorderLayout());
        lower.add(messageLabel, BorderLayout.WEST);
        lower.add(pagination, BorderLayout.EAST);
        footer.add(actions, BorderLayout.NORTH);
        footer.add(lower, BorderLayout.SOUTH);
        return footer;
    }

    private void configureSelectionButtons(final JButton editButton,
            final JButton deleteButton, final JButton detailsButton) {
        editButton.setEnabled(false);
        deleteButton.setEnabled(false);
        detailsButton.setEnabled(false);
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent event) {
                if (!event.getValueIsAdjusting()) {
                    boolean selected = table.getSelectedRow() >= 0;
                    editButton.setEnabled(selected);
                    deleteButton.setEnabled(selected);
                    detailsButton.setEnabled(selected);
                }
            }
        });
    }

    private void bindAction(JButton button, final String action) {
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                handleAction(action);
            }
        });
    }

    private void handleAction(String action) {
        if ("add".equals(action)) {
            showCustomerForm(null);
        } else if ("edit".equals(action)) {
            showCustomerForm(selectedCustomer());
        } else if ("delete".equals(action)) {
            deleteCustomer();
        } else if ("details".equals(action)) {
            showDetails();
        } else if ("refresh".equals(action)) {
            searchCustomers();
        } else if ("export".equals(action)) {
            CsvExport.exportCurrentPage(CustomersPanel.this, table, "customers.csv");
        }
    }

    private void installListeners() {
        textField.getDocument().addDocumentListener(new DocumentListener() {
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
        ActionListener filtersChanged = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                pagination.setPageNumber(1);
                searchCustomers();
            }
        };
        typeBox.addActionListener(filtersChanged);
        expiryBox.addActionListener(filtersChanged);
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
                    searchCustomers();
                }
            }
        });
    }

    private void scheduleSearch() {
        searchTimer.restart();
    }

    private void searchCustomers() {
        final String searchText = textField.getText().trim();
        final String customerType = "Any type".equals(typeBox.getSelectedItem())
                ? null : (String) typeBox.getSelectedItem();
        final Integer expiryDays = selectedExpiryDays();
        final int page = pagination.getPageNumber();
        final int pageSize = pagination.getPageSize();
        final String requestedSort = sortKey;
        final boolean requestedAscending = ascending;
        messageLabel.setText("Searching customers...");
        searchTask.execute(this, new BackgroundTask.Work<SearchData>() {
            @Override
            public SearchData run() throws LfrlmsException {
                long count = customerService.countSearchResults(
                        searchText, customerType, expiryDays);
                ArrayList<Customer> customers = customerService.search(searchText,
                        customerType, expiryDays, requestedSort, requestedAscending,
                        page, pageSize);
                return new SearchData(customers, count);
            }
        }, new BackgroundTask.Completion<SearchData>() {
            @Override
            public void completed(SearchData data, Exception failure) {
                if (failure != null) {
                    messageLabel.setText(" ");
                    GuiSupport.showFailure(CustomersPanel.this, "Search customers", failure,
                            new Runnable() {
                                @Override
                                public void run() {
                                    searchCustomers();
                                }
                            });
                    return;
                }
                pagination.setTotalRows(data.count);
                if (pagination.getPageNumber() != page) {
                    searchCustomers();
                    return;
                }
                visibleCustomers = data.customers;
                showCustomers(data.customers);
                messageLabel.setText(data.count + " customers found.");
            }
        });
    }

    private Integer selectedExpiryDays() {
        String selection = (String) expiryBox.getSelectedItem();
        if (selection == null || selection.startsWith("Any")) {
            return null;
        }
        if (selection.contains("30")) {
            return Integer.valueOf(30);
        }
        if (selection.contains("60")) {
            return Integer.valueOf(60);
        }
        return Integer.valueOf(90);
    }

    private void showCustomers(ArrayList<Customer> customers) {
        Vector<Vector<Object>> rows = new Vector<Vector<Object>>();
        for (Customer customer : customers) {
            Vector<Object> row = new Vector<Object>();
            row.add(Integer.valueOf(customer.getCustomerId()));
            row.add(customer.getCustomerType());
            row.add(customer.getDisplayName());
            row.add(customer instanceof CorporateCustomer
                    ? customer.getContactName() : "-");
            row.add(customer.getEmail());
            row.add(customer.getPhone());
            if (customer instanceof IndividualCustomer) {
                IndividualCustomer individual = (IndividualCustomer) customer;
                row.add(individual.getLicenseNo());
                row.add(DateUtil.formatDate(individual.getLicenseExpiry()));
            } else if (customer instanceof CorporateCustomer) {
                CorporateCustomer corporate = (CorporateCustomer) customer;
                row.add(corporate.getGstNo());
                row.add("—");
            }
            rows.add(row);
        }
        tableModel.replaceRows(rows);
    }

    private void showCustomerForm(Customer customer) {
        Customer value = CustomerFormDialog.showDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this), customer);
        if (value == null) {
            return;
        }
        writeTask.execute(this, new BackgroundTask.Work<Integer>() {
            @Override
            public Integer run() throws LfrlmsException {
                return Integer.valueOf(customerService.save(value));
            }
        }, new BackgroundTask.Completion<Integer>() {
            @Override
            public void completed(Integer id, Exception failure) {
                if (failure != null) {
                    GuiSupport.showFailure(CustomersPanel.this, "Save customer", failure, null);
                    return;
                }
                messageLabel.setText("Customer saved.");
                searchCustomers();
            }
        });
    }

    private void deleteCustomer() {
        final Customer customer = selectedCustomer();
        if (customer == null) {
            return;
        }
        deleteCheckTask.execute(this, new BackgroundTask.Work<Boolean>() {
            @Override
            public Boolean run() throws LfrlmsException {
                return Boolean.valueOf(customerService.canDelete(customer.getCustomerId()));
            }
        }, new BackgroundTask.Completion<Boolean>() {
            @Override
            public void completed(Boolean allowed, Exception failure) {
                if (failure != null) {
                    GuiSupport.showFailure(CustomersPanel.this, "Check customer deletion",
                            failure, null);
                    return;
                }
                if (!Boolean.TRUE.equals(allowed)) {
                    JOptionPane.showMessageDialog(CustomersPanel.this,
                            CustomerService.HISTORY_DELETE_MESSAGE,
                            "Customer cannot be deleted", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                confirmAndDelete(customer);
            }
        });
    }

    private void confirmAndDelete(final Customer customer) {
        int choice = JOptionPane.showConfirmDialog(this,
                "Permanently delete " + customer.getDisplayName() + "?",
                "Confirm customer deletion", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        writeTask.execute(this, new BackgroundTask.Work<Integer>() {
            @Override
            public Integer run() throws LfrlmsException {
                return Integer.valueOf(customerService.delete(customer.getCustomerId()) ? 1 : 0);
            }
        }, new BackgroundTask.Completion<Integer>() {
            @Override
            public void completed(Integer result, Exception failure) {
                if (failure != null) {
                    GuiSupport.showFailure(CustomersPanel.this, "Delete customer", failure, null);
                    return;
                }
                messageLabel.setText("Customer deleted.");
                searchCustomers();
            }
        });
    }

    private void showDetails() {
        Customer customer = selectedCustomer();
        if (customer == null) {
            return;
        }
        CustomerDetailsDialog dialog = new CustomerDetailsDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this), customer, reportService);
        dialog.setVisible(true);
    }

    private Customer selectedCustomer() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= visibleCustomers.size()) {
            return null;
        }
        return visibleCustomers.get(row);
    }

    private String sortKeyForColumn(int column) {
        if (column == 0) {
            return "id";
        }
        if (column == 2 || column == 3) {
            return "lastName";
        }
        if (column == 4) {
            return "email";
        }
        return null;
    }

    private void addFilter(JPanel panel, int row, String label,
            java.awt.Component control) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.anchor = GridBagConstraints.LINE_END;
        constraints.insets = new Insets(3, 8, 3, 6);
        panel.add(new JLabel(label + ":"), constraints);
        constraints = new GridBagConstraints();
        constraints.gridx = 1;
        constraints.gridy = row;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(3, 3, 3, 8);
        panel.add(control, constraints);
    }

    private static class SearchData {
        private final ArrayList<Customer> customers;
        private final long count;

        private SearchData(ArrayList<Customer> customers, long count) {
            this.customers = customers;
            this.count = count;
        }
    }
}
