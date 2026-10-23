package lfrlms.gui;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import lfrlms.util.DateUtil;

/**
 * Purpose: Captures pickup or return details before a rental service call.
 * Concept: Swing dialogs, input parsing and small immutable result objects.
 * Layer: GUI (presentation).
 * Rubric: GUI, inner/anonymous classes.
 */
public class RentalActionDialog extends JDialog {
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final boolean pickup;
    private final JTextField dateTimeField;
    private final JTextField odometerField;
    private final JTextField conditionField;
    private final JLabel messageLabel;
    private RentalAction value;

    private RentalActionDialog(Window owner, boolean pickup) {
        super(owner, pickup ? "Start Rental" : "Return Vehicle",
                Dialog.ModalityType.APPLICATION_MODAL);
        this.pickup = pickup;
        dateTimeField = new JTextField(DATE_TIME_FORMAT.format(LocalDateTime.now()), 20);
        odometerField = new JTextField(20);
        conditionField = new JTextField(20);
        messageLabel = new JLabel(" ");
        buildForm();
    }

    public static RentalAction showStartDialog(Window owner) {
        return show(owner, true);
    }

    public static RentalAction showReturnDialog(Window owner) {
        return show(owner, false);
    }

    private static RentalAction show(Window owner, boolean pickup) {
        RentalActionDialog dialog = new RentalActionDialog(owner, pickup);
        dialog.setVisible(true);
        RentalAction result = dialog.value;
        dialog.dispose();
        return result;
    }

    private void buildForm() {
        JPanel fields = new JPanel(new GridBagLayout());
        addRow(fields, 0, pickup ? "Pickup time" : "Return time", dateTimeField);
        addRow(fields, 1, pickup ? "Odometer out" : "Odometer in", odometerField);
        addRow(fields, 2, pickup ? "Condition out" : "Condition in", conditionField);
        JPanel buttons = new JPanel();
        JButton saveButton = new JButton(pickup ? "Start" : "Complete Return");
        JButton cancelButton = new JButton("Cancel");
        buttons.add(saveButton);
        buttons.add(cancelButton);
        saveButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                capture();
            }
        });
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
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
        try {
            LocalDateTime dateTime = DateUtil.parseDateTime(
                    dateTimeField.getText().trim().replace(' ', 'T'));
            int odometer = Integer.parseInt(odometerField.getText().trim());
            String condition = conditionField.getText().trim();
            if (dateTime == null || odometer < 0 || condition.isEmpty()) {
                throw new IllegalArgumentException();
            }
            value = new RentalAction(dateTime, odometer, condition);
            dispose();
        } catch (RuntimeException exception) {
            messageLabel.setText("Enter a valid time, non-negative odometer and condition.");
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

    public static class RentalAction {
        private final LocalDateTime dateTime;
        private final int odometer;
        private final String condition;

        private RentalAction(LocalDateTime dateTime, int odometer, String condition) {
            this.dateTime = dateTime;
            this.odometer = odometer;
            this.condition = condition;
        }

        public LocalDateTime getDateTime() {
            return dateTime;
        }

        public int getOdometer() {
            return odometer;
        }

        public String getCondition() {
            return condition;
        }
    }
}
