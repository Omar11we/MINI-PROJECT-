package lfrlms.gui;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Vector;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import lfrlms.exception.LfrlmsException;
import lfrlms.model.Rental;
import lfrlms.model.Vehicle;
import lfrlms.service.RentalService;
import lfrlms.util.DateUtil;

/**
 * Purpose: Shows vehicle facts and its next confirmed reservations.
 * Concept: Swing detail views and background service calls.
 * Layer: GUI (presentation).
 * Rubric: GUI, inner/anonymous classes.
 */
public class VehicleDetailsDialog extends JDialog {
    private final Vehicle vehicle;
    private final String brandName;
    private final String categoryName;
    private final String branchName;
    private final RentalService rentalService;
    private final SharedTableModel tableModel;
    private final JLabel messageLabel;
    private final BackgroundTask<ArrayList<Rental>> task;

    public VehicleDetailsDialog(Window owner, Vehicle vehicle, String brandName,
            String categoryName, String branchName, RentalService rentalService) {
        super(owner, "Vehicle Details", Dialog.ModalityType.MODELESS);
        this.vehicle = vehicle;
        this.brandName = brandName;
        this.categoryName = categoryName;
        this.branchName = branchName;
        this.rentalService = rentalService;
        tableModel = new SharedTableModel(new String[] {
            "Rental #", "Scheduled start", "Expected return", "Status"});
        messageLabel = new JLabel("Loading upcoming rentals...");
        task = new BackgroundTask<ArrayList<Rental>>();
        buildWindow();
        loadUpcoming();
    }

    private void buildWindow() {
        JPanel facts = new JPanel(new java.awt.GridLayout(0, 2, 10, 5));
        addFact(facts, "Registration", vehicle.getRegistrationNo());
        addFact(facts, "Brand", brandName);
        addFact(facts, "Category", categoryName);
        addFact(facts, "Branch", branchName);
        addFact(facts, "Model", vehicle.getModel() + " (" + vehicle.getModelYear() + ")");
        addFact(facts, "Status", vehicle.getStatus());
        addFact(facts, "Rental rate / day", money(vehicle.getRentalRate()));
        addFact(facts, "Lease rate / month", money(vehicle.getLeaseRate()));
        JTable table = SharedTableModel.createTable(tableModel);
        table.setFillsViewportHeight(true);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(690, 250));
        JPanel center = new JPanel(new BorderLayout(5, 5));
        center.add(new JLabel("Next confirmed rentals"), BorderLayout.NORTH);
        center.add(scroll, BorderLayout.CENTER);
        center.add(messageLabel, BorderLayout.SOUTH);
        add(facts, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        Theme.applyDialogPadding(this);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(740, 430);
        setLocationRelativeTo(getOwner());
    }

    private void addFact(JPanel panel, String label, String value) {
        JLabel key = new JLabel(label + ":");
        key.setHorizontalAlignment(SwingConstants.RIGHT);
        panel.add(key);
        panel.add(new JLabel(value == null ? "—" : value));
    }

    private String money(java.math.BigDecimal value) {
        return value == null ? "—" : value.toPlainString();
    }

    private void loadUpcoming() {
        task.execute(this, new BackgroundTask.Work<ArrayList<Rental>>() {
            @Override
            public ArrayList<Rental> run() throws LfrlmsException {
                return rentalService.upcomingForVehicle(vehicle.getVehicleId(), 20);
            }
        }, new BackgroundTask.Completion<ArrayList<Rental>>() {
            @Override
            public void completed(ArrayList<Rental> rentals, Exception failure) {
                if (failure != null) {
                    messageLabel.setText(" ");
                    GuiSupport.showFailure(VehicleDetailsDialog.this,
                            "Load upcoming rentals", failure, null);
                    return;
                }
                Vector<Vector<Object>> rows = new Vector<Vector<Object>>();
                for (Rental rental : rentals) {
                    Vector<Object> row = new Vector<Object>();
                    row.add(Integer.valueOf(rental.getRentalId()));
                    row.add(DateUtil.formatDateTime(rental.getScheduledStartDatetime()));
                    row.add(DateUtil.formatDateTime(rental.getExpectedReturnDatetime()));
                    row.add(rental.getStatus());
                    rows.add(row);
                }
                tableModel.replaceRows(rows);
                messageLabel.setText(rentals.isEmpty()
                        ? "No future confirmed rentals." : "Showing the next " + rentals.size());
            }
        });
    }
}
