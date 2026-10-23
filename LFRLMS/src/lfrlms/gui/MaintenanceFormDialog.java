package lfrlms.gui;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import lfrlms.exception.LfrlmsException;
import lfrlms.model.Maintenance;
import lfrlms.model.Vehicle;
import lfrlms.service.VehicleService;
import lfrlms.util.DateUtil;

/**
 * Purpose: Captures a maintenance request and vehicle choice.
 * Concept: Swing forms, copy-safe model input and asynchronous lookups.
 * Layer: GUI (presentation).
 * Rubric: GUI, inner/anonymous classes.
 */
public class MaintenanceFormDialog extends JDialog {
    private static final Color ERROR_COLOR = new Color(150, 45, 35);
    private final VehicleService vehicleService;
    private final BackgroundTask<ArrayList<Vehicle>> loadTask;
    private final JComboBox<VehicleChoice> vehicleBox;
    private final JTextField serviceDateField;
    private final JTextField serviceTypeField;
    private final JTextField descriptionField;
    private final JTextField costField;
    private final JTextField nextServiceField;
    private final JLabel messageLabel;
    private Maintenance maintenance;

    private MaintenanceFormDialog(Window owner, VehicleService vehicleService) {
        super(owner, "Open Maintenance", Dialog.ModalityType.APPLICATION_MODAL);
        this.vehicleService = vehicleService;
        loadTask = new BackgroundTask<ArrayList<Vehicle>>();
        vehicleBox = new JComboBox<VehicleChoice>();
        serviceDateField = new JTextField(DateUtil.formatDate(LocalDate.now()), 18);
        serviceTypeField = new JTextField(18);
        descriptionField = new JTextField(18);
        costField = new JTextField(18);
        nextServiceField = new JTextField(18);
        messageLabel = new JLabel("Loading vehicles...");
        buildForm();
        loadVehicles();
    }

    public static Maintenance showDialog(Window owner, VehicleService vehicleService) {
        MaintenanceFormDialog dialog = new MaintenanceFormDialog(owner, vehicleService);
        dialog.setVisible(true);
        Maintenance result = dialog.maintenance;
        dialog.dispose();
        return result;
    }

    private void buildForm() {
        JPanel fields = new JPanel(new GridBagLayout());
        addRow(fields, 0, "Vehicle", vehicleBox);
        addRow(fields, 1, "Service date (yyyy-MM-dd)", serviceDateField);
        addRow(fields, 2, "Service type", serviceTypeField);
        addRow(fields, 3, "Description", descriptionField);
        addRow(fields, 4, "Cost", costField);
        addRow(fields, 5, "Next service (optional)", nextServiceField);
        JPanel buttons = new JPanel();
        JButton openButton = new JButton("Open Maintenance");
        JButton cancelButton = new JButton("Cancel");
        buttons.add(openButton);
        buttons.add(cancelButton);
        openButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                capture();
            }
        });
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                dispose();
            }
        });
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(messageLabel, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.SOUTH);
        add(fields, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        Theme.applyDialogPadding(this);
        getRootPane().setDefaultButton(openButton);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "close-dialog");
        getRootPane().getActionMap().put("close-dialog", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                dispose();
            }
        });
        pack();
        setLocationRelativeTo(getOwner());
    }

    private void loadVehicles() {
        loadTask.execute(this, new BackgroundTask.Work<ArrayList<Vehicle>>() {
            @Override
            public ArrayList<Vehicle> run() throws LfrlmsException {
                return vehicleService.search(null, null, null, null, null,
                        null, null, null, null, null, null,
                        "registrationNo", true, 1, 500);
            }
        }, new BackgroundTask.Completion<ArrayList<Vehicle>>() {
            @Override
            public void completed(ArrayList<Vehicle> vehicles, Exception failure) {
                if (failure != null) {
                    messageLabel.setForeground(Theme.TEXT_COLOR);
                    messageLabel.setText(" ");
                    GuiSupport.showFailure(MaintenanceFormDialog.this,
                            "Load maintenance vehicles", failure, new Runnable() {
                                @Override
                                public void run() {
                                    loadVehicles();
                                }
                            });
                    return;
                }
                vehicleBox.removeAllItems();
                for (Vehicle vehicle : vehicles) {
                    vehicleBox.addItem(new VehicleChoice(vehicle));
                }
                if (vehicles.isEmpty()) {
                    messageLabel.setForeground(ERROR_COLOR);
                    messageLabel.setText("No vehicles are available in the list.");
                } else {
                    messageLabel.setForeground(Theme.TEXT_COLOR);
                    messageLabel.setText(" ");
                }
            }
        });
    }

    private void capture() {
        VehicleChoice choice = (VehicleChoice) vehicleBox.getSelectedItem();
        if (choice == null) {
            messageLabel.setForeground(ERROR_COLOR);
            messageLabel.setText("Choose a vehicle.");
            return;
        }
        try {
            LocalDate serviceDate = DateUtil.parseDate(serviceDateField.getText().trim());
            LocalDate nextService = DateUtil.parseDate(nextServiceField.getText().trim());
            String serviceType = serviceTypeField.getText().trim();
            String costText = costField.getText().trim();
            BigDecimal cost = new BigDecimal(costText);
            if (serviceDate == null || serviceType.isEmpty() || cost.signum() < 0) {
                throw new IllegalArgumentException();
            }
            maintenance = new Maintenance();
            maintenance.setVehicleId(choice.vehicle.getVehicleId());
            maintenance.setServiceDate(serviceDate);
            maintenance.setServiceType(serviceType);
            maintenance.setDescription(descriptionField.getText().trim());
            maintenance.setCost(cost);
            maintenance.setNextServiceDate(nextService);
            dispose();
        } catch (RuntimeException exception) {
            messageLabel.setForeground(ERROR_COLOR);
            messageLabel.setText("Check the date, service type and non-negative cost.");
        }
    }

    private void addRow(JPanel panel, int row, String title, JComponent component) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.anchor = GridBagConstraints.LINE_END;
        left.insets = new Insets(5, 8, 5, 8);
        panel.add(new JLabel(title + ":"), left);
        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row;
        right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(5, 4, 5, 8);
        panel.add(component, right);
    }

    private static class VehicleChoice {
        private final Vehicle vehicle;

        private VehicleChoice(Vehicle vehicle) {
            this.vehicle = vehicle;
        }

        @Override
        public String toString() {
            return vehicle.getRegistrationNo() + " — " + vehicle.getModel()
                    + " (" + vehicle.getStatus() + ")";
        }
    }
}
