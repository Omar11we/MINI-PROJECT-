package lfrlms.gui;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import lfrlms.model.Driver;
import lfrlms.util.Constants;

/**
 * Purpose: Captures or edits chauffeur contact and licence details.
 * Concept: Swing forms and copy constructors.
 * Layer: GUI (presentation).
 * Rubric: GUI, inner/anonymous classes.
 */
public class DriverFormDialog extends JDialog {
    private final Driver driver;
    private final JTextField firstNameField;
    private final JTextField lastNameField;
    private final JTextField phoneField;
    private final JTextField licenseField;
    private final JComboBox<String> statusBox;
    private final JLabel messageLabel;
    private boolean saved;

    private DriverFormDialog(Window owner, Driver existing) {
        super(owner, existing == null ? "Add Driver" : "Edit Driver",
                Dialog.ModalityType.APPLICATION_MODAL);
        driver = existing == null ? new Driver() : new Driver(existing);
        firstNameField = new JTextField(value(driver.getFirstName()), 22);
        lastNameField = new JTextField(value(driver.getLastName()), 22);
        phoneField = new JTextField(value(driver.getPhone()), 22);
        licenseField = new JTextField(value(driver.getLicenseNo()), 22);
        statusBox = new JComboBox<String>(new String[] {
            Constants.DRIVER_ACTIVE, Constants.DRIVER_INACTIVE});
        statusBox.setSelectedItem(driver.getStatus());
        messageLabel = new JLabel(" ");
        saved = false;
        buildForm();
    }

    public static Driver showDialog(Window owner, Driver existing) {
        DriverFormDialog dialog = new DriverFormDialog(owner, existing);
        dialog.setVisible(true);
        Driver result = dialog.saved ? dialog.driver : null;
        dialog.dispose();
        return result;
    }

    private void buildForm() {
        JPanel fields = new JPanel(new GridBagLayout());
        addRow(fields, 0, "First name", firstNameField);
        addRow(fields, 1, "Last name", lastNameField);
        addRow(fields, 2, "Phone", phoneField);
        addRow(fields, 3, "Licence number", licenseField);
        addRow(fields, 4, "Status", statusBox);
        JPanel buttons = new JPanel();
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");
        buttons.add(saveButton);
        buttons.add(cancelButton);
        saveButton.addActionListener(new ActionListener() {
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
        getRootPane().setDefaultButton(saveButton);
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

    private void capture() {
        driver.setFirstName(firstNameField.getText().trim());
        driver.setLastName(lastNameField.getText().trim());
        driver.setPhone(phoneField.getText().trim());
        driver.setLicenseNo(licenseField.getText().trim());
        driver.setStatus((String) statusBox.getSelectedItem());
        if (driver.getFirstName().isEmpty() || driver.getLastName().isEmpty()
                || driver.getPhone().isEmpty() || driver.getLicenseNo().isEmpty()) {
            messageLabel.setText("Complete every driver field.");
            return;
        }
        saved = true;
        dispose();
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

    private String value(String text) {
        return text == null ? "" : text;
    }
}
