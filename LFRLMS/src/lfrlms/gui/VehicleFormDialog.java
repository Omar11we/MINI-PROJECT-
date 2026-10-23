package lfrlms.gui;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.util.ArrayList;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import lfrlms.model.Branch;
import lfrlms.model.Brand;
import lfrlms.model.Vehicle;
import lfrlms.model.VehicleCategory;
import lfrlms.util.Validator;

/**
 * Purpose: Edits a copy of a vehicle and returns it only after valid input.
 * Concept: Swing dialog, validation and copy constructors.
 * Layer: GUI (presentation).
 * Rubric: GUI, inner/anonymous classes.
 */
public class VehicleFormDialog extends JDialog {
    private final Vehicle vehicle;
    private final JTextField registrationField;
    private final JTextField modelField;
    private final JTextField yearField;
    private final JTextField rentalRateField;
    private final JTextField leaseRateField;
    private final JComboBox<LookupChoice> brandBox;
    private final JComboBox<LookupChoice> categoryBox;
    private final JComboBox<LookupChoice> branchBox;
    private final JLabel messageLabel;
    private boolean saved;

    public VehicleFormDialog(Window owner, Vehicle existing, ArrayList<Brand> brands,
            ArrayList<VehicleCategory> categories, ArrayList<Branch> branches) {
        super(owner, existing == null ? "Add Vehicle" : "Edit Vehicle",
                Dialog.ModalityType.APPLICATION_MODAL);
        vehicle = existing == null ? new Vehicle() : new Vehicle(existing);
        registrationField = new JTextField(value(vehicle.getRegistrationNo()), 22);
        modelField = new JTextField(value(vehicle.getModel()), 22);
        yearField = new JTextField(vehicle.getModelYear() <= 0 ? "" :
                String.valueOf(vehicle.getModelYear()), 22);
        rentalRateField = new JTextField(value(vehicle.getRentalRate()), 22);
        leaseRateField = new JTextField(value(vehicle.getLeaseRate()), 22);
        brandBox = buildBrandBox(brands, vehicle.getBrandId());
        categoryBox = buildCategoryBox(categories, vehicle.getCategoryId());
        branchBox = buildBranchBox(branches, vehicle.getBranchId());
        messageLabel = new JLabel(" ");
        saved = false;
        buildForm();
    }

    public static Vehicle showDialog(Window owner, Vehicle existing,
            ArrayList<Brand> brands, ArrayList<VehicleCategory> categories,
            ArrayList<Branch> branches) {
        VehicleFormDialog dialog = new VehicleFormDialog(owner, existing,
                brands, categories, branches);
        dialog.setVisible(true);
        Vehicle result = dialog.saved ? dialog.vehicle : null;
        dialog.dispose();
        return result;
    }

    private void buildForm() {
        JPanel fields = new JPanel(new GridBagLayout());
        addRow(fields, 0, "Registration number", registrationField);
        addRow(fields, 1, "Brand", brandBox);
        addRow(fields, 2, "Category", categoryBox);
        addRow(fields, 3, "Branch", branchBox);
        addRow(fields, 4, "Model", modelField);
        addRow(fields, 5, "Model year", yearField);
        addRow(fields, 6, "Rental rate per day", rentalRateField);
        addRow(fields, 7, "Lease rate per month", leaseRateField);
        JPanel buttons = new JPanel();
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");
        buttons.add(saveButton);
        buttons.add(cancelButton);
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                saveVehicle();
            }
        });
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                dispose();
            }
        });
        messageLabel.setForeground(new java.awt.Color(170, 35, 35));
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(messageLabel, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.SOUTH);
        add(fields, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        Theme.applyDialogPadding(this);
        getRootPane().setDefaultButton(saveButton);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "close-dialog");
        getRootPane().getActionMap().put("close-dialog", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                dispose();
            }
        });
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(getOwner());
    }

    private void saveVehicle() {
        String registration = registrationField.getText().trim().toUpperCase();
        String model = modelField.getText().trim();
        int year;
        BigDecimal rentalRate;
        BigDecimal leaseRate;
        try {
            year = Integer.parseInt(yearField.getText().trim());
            rentalRate = parseMoney(rentalRateField.getText());
            leaseRate = parseMoney(leaseRateField.getText());
        } catch (NumberFormatException exception) {
            messageLabel.setText("Enter a valid model year and rate.");
            return;
        }
        if (!Validator.isValidRegistrationNo(registration)
                || !Validator.isValid(model) || !Validator.isInRange(
                Integer.valueOf(year), 2000, 2100)
                || selectedId(brandBox) <= 0 || selectedId(categoryBox) <= 0
                || selectedId(branchBox) <= 0
                || (rentalRate == null && leaseRate == null)
                || (rentalRate != null && !Validator.isNonNegative(rentalRate))
                || (leaseRate != null && !Validator.isNonNegative(leaseRate))) {
            messageLabel.setText("Check the registration, lookup choices, year and rates.");
            return;
        }
        vehicle.setRegistrationNo(registration);
        vehicle.setBrandId(selectedId(brandBox));
        vehicle.setCategoryId(selectedId(categoryBox));
        vehicle.setBranchId(selectedId(branchBox));
        vehicle.setModel(model);
        vehicle.setModelYear(year);
        vehicle.setRentalRate(rentalRate);
        vehicle.setLeaseRate(leaseRate);
        saved = true;
        dispose();
    }

    private BigDecimal parseMoney(String value) {
        String text = value == null ? "" : value.trim();
        return text.isEmpty() ? null : new BigDecimal(text);
    }

    private JComboBox<LookupChoice> buildBrandBox(ArrayList<Brand> items, int selectedId) {
        JComboBox<LookupChoice> box = new JComboBox<LookupChoice>();
        box.addItem(new LookupChoice(-1, "Select brand"));
        for (Brand item : items) {
            box.addItem(new LookupChoice(item.getBrandId(), item.getBrandName()));
        }
        selectChoice(box, selectedId);
        return box;
    }

    private JComboBox<LookupChoice> buildCategoryBox(ArrayList<VehicleCategory> items,
            int selectedId) {
        JComboBox<LookupChoice> box = new JComboBox<LookupChoice>();
        box.addItem(new LookupChoice(-1, "Select category"));
        for (VehicleCategory item : items) {
            box.addItem(new LookupChoice(item.getCategoryId(), item.getCategoryName()));
        }
        selectChoice(box, selectedId);
        return box;
    }

    private JComboBox<LookupChoice> buildBranchBox(ArrayList<Branch> items, int selectedId) {
        JComboBox<LookupChoice> box = new JComboBox<LookupChoice>();
        box.addItem(new LookupChoice(-1, "Select branch"));
        for (Branch item : items) {
            box.addItem(new LookupChoice(item.getBranchId(), item.getBranchName()));
        }
        selectChoice(box, selectedId);
        return box;
    }

    private int selectedId(JComboBox<LookupChoice> box) {
        LookupChoice choice = (LookupChoice) box.getSelectedItem();
        return choice == null ? -1 : choice.id;
    }

    private void selectChoice(JComboBox<LookupChoice> box, int id) {
        for (int index = 0; index < box.getItemCount(); index++) {
            if (box.getItemAt(index).id == id) {
                box.setSelectedIndex(index);
                return;
            }
        }
    }

    private void addRow(JPanel panel, int row, String label, JComponent field) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.anchor = GridBagConstraints.LINE_END;
        left.insets = new Insets(5, 8, 5, 8);
        panel.add(new JLabel(label + ":"), left);
        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row;
        right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(5, 4, 5, 8);
        panel.add(field, right);
    }

    private String value(Object object) {
        return object == null ? "" : object.toString();
    }

    private static class LookupChoice {
        private final int id;
        private final String name;

        private LookupChoice(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
