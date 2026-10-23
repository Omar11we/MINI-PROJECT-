package lfrlms.gui;

import java.awt.BorderLayout;
import java.awt.Color;
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
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import lfrlms.exception.LfrlmsException;
import lfrlms.model.Lease;
import lfrlms.model.Payment;
import lfrlms.model.Rental;
import lfrlms.service.PaymentService;
import lfrlms.util.Constants;

/**
 * Purpose: Records one payment against either a rental or a lease.
 * Concept: Dynamic Swing forms, wrapper references and BigDecimal input.
 * Layer: GUI (presentation).
 * Rubric: GUI, wrapper classes.
 */
public class PaymentFormDialog extends JDialog {
    private final PaymentService paymentService;
    private final BackgroundTask<ReferenceData> loadTask;
    private final BackgroundTask<BigDecimal> balanceTask;
    private final BackgroundTask<Integer> saveTask;
    private final JComboBox<String> typeBox;
    private final JComboBox<ReferenceChoice> referenceBox;
    private final JComboBox<String> methodBox;
    private final JTextField amountField;
    private final JLabel balanceLabel;
    private final JLabel messageLabel;
    private ArrayList<Rental> rentals;
    private ArrayList<Lease> leases;
    private boolean saved;

    private PaymentFormDialog(Window owner, PaymentService paymentService) {
        super(owner, "Record Payment", Dialog.ModalityType.APPLICATION_MODAL);
        this.paymentService = paymentService;
        loadTask = new BackgroundTask<ReferenceData>();
        balanceTask = new BackgroundTask<BigDecimal>();
        saveTask = new BackgroundTask<Integer>();
        typeBox = new JComboBox<String>(new String[] {"Rental", "Lease"});
        referenceBox = new JComboBox<ReferenceChoice>();
        methodBox = new JComboBox<String>(new String[] {Constants.PAYMENT_CASH,
            Constants.PAYMENT_CARD, Constants.PAYMENT_BANK_TRANSFER, Constants.PAYMENT_UPI});
        amountField = new JTextField(20);
        balanceLabel = new JLabel("Outstanding: loading...");
        messageLabel = new JLabel("Loading eligible contracts...");
        rentals = new ArrayList<Rental>();
        leases = new ArrayList<Lease>();
        saved = false;
        buildForm();
        installListeners();
        loadReferences();
    }

    public static boolean showDialog(Window owner, PaymentService paymentService) {
        PaymentFormDialog dialog = new PaymentFormDialog(owner, paymentService);
        dialog.setVisible(true);
        boolean result = dialog.saved;
        dialog.dispose();
        return result;
    }

    private void buildForm() {
        JPanel fields = new JPanel(new GridBagLayout());
        addRow(fields, 0, "Payment applies to", typeBox);
        addRow(fields, 1, "Contract", referenceBox);
        addRow(fields, 2, "Method", methodBox);
        addRow(fields, 3, "Amount", amountField);
        addRow(fields, 4, "", balanceLabel);
        JPanel buttons = new JPanel();
        JButton saveButton = new JButton("Record Success Payment");
        JButton cancelButton = new JButton("Cancel");
        buttons.add(saveButton);
        buttons.add(cancelButton);
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                savePayment();
            }
        });
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                dispose();
            }
        });
        JPanel bottom = new JPanel(new BorderLayout());
        messageLabel.setForeground(new Color(150, 45, 35));
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

    private void installListeners() {
        typeBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                replaceReferenceChoices();
            }
        });
        referenceBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                refreshBalance();
            }
        });
    }

    private void loadReferences() {
        loadTask.execute(this, new BackgroundTask.Work<ReferenceData>() {
            @Override
            public ReferenceData run() throws LfrlmsException {
                return new ReferenceData(paymentService.listBillableRentals(),
                        paymentService.listBillableLeases());
            }
        }, new BackgroundTask.Completion<ReferenceData>() {
            @Override
            public void completed(ReferenceData data, Exception failure) {
                if (failure != null) {
                    messageLabel.setText(" ");
                    GuiSupport.showFailure(PaymentFormDialog.this,
                            "Load payment references", failure, new Runnable() {
                                @Override
                                public void run() {
                                    loadReferences();
                                }
                            });
                    return;
                }
                rentals = data.rentals;
                leases = data.leases;
                messageLabel.setText("Choose one contract. The service enforces one reference.");
                replaceReferenceChoices();
            }
        });
    }

    private void replaceReferenceChoices() {
        ReferenceChoice previous = (ReferenceChoice) referenceBox.getSelectedItem();
        referenceBox.removeAllItems();
        if ("Rental".equals(typeBox.getSelectedItem())) {
            for (Rental rental : rentals) {
                referenceBox.addItem(new ReferenceChoice(rental));
            }
        } else {
            for (Lease lease : leases) {
                referenceBox.addItem(new ReferenceChoice(lease));
            }
        }
        if (previous != null) {
            for (int index = 0; index < referenceBox.getItemCount(); index++) {
                if (referenceBox.getItemAt(index).id == previous.id) {
                    referenceBox.setSelectedIndex(index);
                    break;
                }
            }
        }
        refreshBalance();
    }

    private void refreshBalance() {
        final ReferenceChoice choice = (ReferenceChoice) referenceBox.getSelectedItem();
        if (choice == null) {
            balanceLabel.setText("Outstanding: no billable contracts");
            return;
        }
        balanceLabel.setText("Outstanding: loading...");
        balanceTask.execute(this, new BackgroundTask.Work<BigDecimal>() {
            @Override
            public BigDecimal run() throws LfrlmsException {
                return choice.rental
                        ? paymentService.rentalOutstanding(choice.id)
                        : paymentService.leaseOutstanding(choice.id);
            }
        }, new BackgroundTask.Completion<BigDecimal>() {
            @Override
            public void completed(BigDecimal balance, Exception failure) {
                if (failure != null) {
                    balanceLabel.setText(" ");
                    GuiSupport.showFailure(PaymentFormDialog.this,
                            "Load outstanding balance", failure, null);
                    return;
                }
                balanceLabel.setText("Outstanding: " + balance.toPlainString());
            }
        });
    }

    private void savePayment() {
        final ReferenceChoice reference = (ReferenceChoice) referenceBox.getSelectedItem();
        final BigDecimal amount;
        try {
            amount = new BigDecimal(amountField.getText().trim());
        } catch (NumberFormatException exception) {
            messageLabel.setText("Enter a valid positive payment amount.");
            return;
        }
        if (reference == null) {
            messageLabel.setText("Choose one rental or lease reference.");
            return;
        }
        final Payment payment = new Payment();
        payment.setRentalId(reference.rental ? Integer.valueOf(reference.id) : null);
        payment.setLeaseId(reference.rental ? null : Integer.valueOf(reference.id));
        payment.setAmount(amount);
        payment.setMethod((String) methodBox.getSelectedItem());
        payment.setStatus(Constants.PAYMENT_SUCCESS);
        messageLabel.setText("Recording payment...");
        saveTask.execute(this, new BackgroundTask.Work<Integer>() {
            @Override
            public Integer run() throws LfrlmsException {
                return Integer.valueOf(paymentService.recordPayment(payment));
            }
        }, new BackgroundTask.Completion<Integer>() {
            @Override
            public void completed(Integer id, Exception failure) {
                if (failure != null) {
                    messageLabel.setText(" ");
                    GuiSupport.showFailure(PaymentFormDialog.this, "Record payment", failure,
                            null);
                    return;
                }
                saved = true;
                dispose();
            }
        });
    }

    private void addRow(JPanel panel, int row, String title, JComponent component) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.anchor = GridBagConstraints.LINE_END;
        left.insets = new Insets(5, 8, 5, 8);
        panel.add(new JLabel(title.isEmpty() ? " " : title + ":"), left);
        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row;
        right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(5, 4, 5, 8);
        panel.add(component, right);
    }

    private static class ReferenceChoice {
        private final int id;
        private final boolean rental;
        private final int customerId;
        private final String status;

        private ReferenceChoice(Rental value) {
            id = value.getRentalId();
            rental = true;
            customerId = value.getCustomerId();
            status = value.getStatus();
        }

        private ReferenceChoice(Lease value) {
            id = value.getLeaseId();
            rental = false;
            customerId = value.getCustomerId();
            status = value.getStatus();
        }

        @Override
        public String toString() {
            return (rental ? "Rental #" : "Lease #") + id + " — customer #"
                    + customerId + " — " + status;
        }
    }

    private static class ReferenceData {
        private final ArrayList<Rental> rentals;
        private final ArrayList<Lease> leases;

        private ReferenceData(ArrayList<Rental> rentals, ArrayList<Lease> leases) {
            this.rentals = rentals;
            this.leases = leases;
        }
    }
}
