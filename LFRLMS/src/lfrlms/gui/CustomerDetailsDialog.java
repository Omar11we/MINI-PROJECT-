package lfrlms.gui;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import lfrlms.exception.LfrlmsException;
import lfrlms.model.CorporateCustomer;
import lfrlms.model.Customer;
import lfrlms.model.IndividualCustomer;
import lfrlms.service.ReportService;
import lfrlms.util.DateUtil;

/**
 * Purpose: Shows customer identity, contract and payment history, and balance.
 * Concept: Polymorphic detail display and paged report data.
 * Layer: GUI (presentation).
 * Rubric: GUI, inner/anonymous classes.
 */
public class CustomerDetailsDialog extends JDialog {
    private static final int PAGE_SIZE = 10;
    private final Customer customer;
    private final ReportService reportService;
    private final SharedTableModel tableModel;
    private final PaginationBar pagination;
    private final JLabel balanceLabel;
    private final JLabel messageLabel;
    private final BackgroundTask<DetailData> task;

    public CustomerDetailsDialog(java.awt.Window owner, Customer customer,
            ReportService reportService) {
        super(owner, "Customer Details", Dialog.ModalityType.MODELESS);
        this.customer = customer;
        this.reportService = reportService;
        tableModel = new SharedTableModel(new String[] {
            "Type", "Reference", "Date", "Vehicle", "Status", "Amount"});
        pagination = new PaginationBar();
        balanceLabel = new JLabel("Outstanding balance: loading...");
        messageLabel = new JLabel(" ");
        task = new BackgroundTask<DetailData>();
        buildWindow();
        pagination.setPageChangeListener(new PaginationBar.PageChangeListener() {
            @Override
            public void pageChanged(int pageNumber, int pageSize) {
                loadPage(pageNumber, pageSize);
            }
        });
        loadPage(1, PAGE_SIZE);
    }

    private void buildWindow() {
        JPanel summary = new JPanel(new java.awt.GridLayout(0, 2, 8, 4));
        addFact(summary, "Customer", customer.getDisplayName());
        addFact(summary, "Type", customer.getCustomerType());
        addFact(summary, "Contact", customer.getContactName());
        addFact(summary, "Email", customer.getEmail());
        addFact(summary, "Phone", customer.getPhone());
        addFact(summary, "Address", customer.getStreet() + ", " + customer.getCity()
                + " " + customer.getPincode());
        if (customer instanceof IndividualCustomer) {
            IndividualCustomer individual = (IndividualCustomer) customer;
            addFact(summary, "Licence", individual.getLicenseNo());
            addFact(summary, "Licence expiry", DateUtil.formatDate(individual.getLicenseExpiry()));
        } else if (customer instanceof CorporateCustomer) {
            CorporateCustomer corporate = (CorporateCustomer) customer;
            addFact(summary, "GST number", corporate.getGstNo());
        }
        JTable table = SharedTableModel.createTable(tableModel);
        table.setFillsViewportHeight(true);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(780, 300));
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(messageLabel, BorderLayout.NORTH);
        bottom.add(pagination, BorderLayout.CENTER);
        bottom.add(balanceLabel, BorderLayout.SOUTH);
        add(summary, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        Theme.applyDialogPadding(this);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(830, 570);
        setLocationRelativeTo(getOwner());
    }

    private void addFact(JPanel panel, String label, String value) {
        panel.add(new JLabel(label + ":"));
        panel.add(new JLabel(value == null ? "—" : value));
    }

    private void loadPage(final int pageNumber, final int pageSize) {
        task.execute(this, new BackgroundTask.Work<DetailData>() {
            @Override
            public DetailData run() throws LfrlmsException {
                ArrayList<HashMap<String, Object>> history =
                        reportService.customerTransactionHistory(
                                customer.getCustomerId(), pageNumber, pageSize);
                long count = reportService.countCustomerTransactionHistory(
                        customer.getCustomerId());
                BigDecimal outstanding = reportService.customerOutstanding(
                        customer.getCustomerId());
                return new DetailData(history, count, outstanding);
            }
        }, new BackgroundTask.Completion<DetailData>() {
            @Override
            public void completed(DetailData data, Exception failure) {
                if (failure != null) {
                    messageLabel.setText(" ");
                    GuiSupport.showFailure(CustomerDetailsDialog.this,
                            "Load customer details", failure, null);
                    return;
                }
                showRows(data.rows);
                pagination.setTotalRows(data.count);
                balanceLabel.setText("Outstanding balance: "
                        + data.outstanding.toPlainString());
                messageLabel.setText("Rentals, leases and payments");
            }
        });
    }

    private void showRows(ArrayList<HashMap<String, Object>> data) {
        Vector<Vector<Object>> rows = new Vector<Vector<Object>>();
        for (HashMap<String, Object> item : data) {
            Vector<Object> row = new Vector<Object>();
            row.add(item.get("transaction_type"));
            row.add(item.get("reference_id"));
            Object date = item.get("transaction_date");
            row.add(date == null ? "" : date.toString());
            row.add(item.get("registration_no"));
            row.add(item.get("status"));
            Object amount = item.get("amount");
            row.add(amount == null ? "—" : amount.toString());
            rows.add(row);
        }
        tableModel.replaceRows(rows);
    }

    private static class DetailData {
        private final ArrayList<HashMap<String, Object>> rows;
        private final long count;
        private final BigDecimal outstanding;

        private DetailData(ArrayList<HashMap<String, Object>> rows, long count,
                BigDecimal outstanding) {
            this.rows = rows;
            this.count = count;
            this.outstanding = outstanding;
        }
    }
}
