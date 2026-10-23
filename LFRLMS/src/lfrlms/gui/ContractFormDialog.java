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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lfrlms.exception.LfrlmsException;
import lfrlms.model.Customer;
import lfrlms.model.Driver;
import lfrlms.service.AvailabilityService;
import lfrlms.service.BillingService;
import lfrlms.service.CustomerService;
import lfrlms.service.DriverService;
import lfrlms.service.LeaseService;
import lfrlms.service.RentalService;
import lfrlms.util.Constants;
import lfrlms.util.DateUtil;

/**
 * Purpose: Creates a scheduled lease, rental reservation or walk-in rental.
 * Concept: Swing dialog, service calls and polymorphic contract models.
 * Layer: GUI (presentation).
 * Rubric: GUI, inner/anonymous classes.
 */
public class ContractFormDialog extends JDialog {
    private static final Color ERROR_COLOR = new Color(150, 45, 35);
    private static final Color SUCCESS_COLOR = new Color(35, 120, 55);
    private static final Color NEUTRAL_COLOR = Theme.TEXT_COLOR;
    private static final DateTimeFormatter INPUT_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter INPUT_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final boolean rentalMode;
    private final Integer preselectedVehicleId;
    private final RentalService rentalService;
    private final LeaseService leaseService;
    private final AvailabilityService availabilityService;
    private final CustomerService customerService;
    private final DriverService driverService;
    private final BillingService billingService;
    private final BackgroundTask<LookupData> lookupTask;
    private final BackgroundTask<ArrayList<HashMap<String, Object>>> availabilityTask;
    private final BackgroundTask<Integer> saveTask;
    private final javax.swing.Timer availabilityTimer;
    private final JComboBox<String> kindBox;
    private final JComboBox<CustomerChoice> customerBox;
    private final JComboBox<VehicleChoice> vehicleBox;
    private final JComboBox<DriverChoice> driverBox;
    private final JTextField startField;
    private final JTextField endField;
    private final JTextField depositField;
    private final JTextField odometerField;
    private final JTextField conditionField;
    private final JLabel estimateLabel;
    private final JLabel messageLabel;
    private final JLabel driverLabel;
    private final JLabel odometerLabel;
    private final JLabel conditionLabel;
    private boolean saved;
    private boolean lookupsReady;

    public ContractFormDialog(Window owner, boolean rentalMode, Integer preselectedVehicleId,
            RentalService rentalService, LeaseService leaseService,
            AvailabilityService availabilityService, CustomerService customerService,
            DriverService driverService, BillingService billingService) {
        super(owner, rentalMode ? "New Rental" : "Schedule Lease",
                Dialog.ModalityType.APPLICATION_MODAL);
        this.rentalMode = rentalMode;
        this.preselectedVehicleId = preselectedVehicleId;
        this.rentalService = rentalService;
        this.leaseService = leaseService;
        this.availabilityService = availabilityService;
        this.customerService = customerService;
        this.driverService = driverService;
        this.billingService = billingService;
        lookupTask = new BackgroundTask<LookupData>();
        availabilityTask = new BackgroundTask<ArrayList<HashMap<String, Object>>>();
        saveTask = new BackgroundTask<Integer>();
        kindBox = new JComboBox<String>(new String[] {"Reservation", "Walk-in"});
        customerBox = new JComboBox<CustomerChoice>();
        vehicleBox = new JComboBox<VehicleChoice>();
        driverBox = new JComboBox<DriverChoice>();
        LocalDateTime start = LocalDateTime.now().plusHours(3).withSecond(0).withNano(0);
        LocalDateTime end = start.plusDays(1);
        startField = new JTextField(rentalMode
                ? INPUT_DATE_TIME.format(start) : INPUT_DATE.format(LocalDate.now().plusDays(1)), 20);
        endField = new JTextField(rentalMode
                ? INPUT_DATE_TIME.format(end) : INPUT_DATE.format(LocalDate.now().plusDays(31)), 20);
        startField.setToolTipText(rentalMode
                ? "Format: yyyy-MM-dd HH:mm" : "Format: yyyy-MM-dd");
        endField.setToolTipText(rentalMode
                ? "Format: yyyy-MM-dd HH:mm" : "Format: yyyy-MM-dd");
        depositField = new JTextField("0.00", 20);
        odometerField = new JTextField(20);
        conditionField = new JTextField(20);
        estimateLabel = new JLabel("Estimate: checking...");
        messageLabel = new JLabel("Loading customers, drivers and available vehicles...");
        driverLabel = new JLabel("Chauffeur:");
        odometerLabel = new JLabel("Odometer out:");
        conditionLabel = new JLabel("Condition out:");
        saved = false;
        lookupsReady = false;
        availabilityTimer = new javax.swing.Timer(350, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                refreshAvailability();
            }
        });
        availabilityTimer.setRepeats(false);
        buildForm();
        installListeners();
        updateKindControls();
        loadLookups();
    }

    public static boolean showDialog(Window owner, boolean rentalMode,
            Integer preselectedVehicleId) {
        ContractFormDialog dialog = new ContractFormDialog(owner, rentalMode,
                preselectedVehicleId, new RentalService(), new LeaseService(),
                new AvailabilityService(), new CustomerService(), new DriverService(),
                new BillingService());
        dialog.setVisible(true);
        boolean result = dialog.saved;
        dialog.dispose();
        return result;
    }

    private void buildForm() {
        JPanel fields = new JPanel(new GridBagLayout());
        int row = 0;
        if (rentalMode) {
            addRow(fields, row++, "Rental type", kindBox);
        }
        addRow(fields, row++, "Customer", customerBox);
        addRow(fields, row++, "Available vehicle", vehicleBox);
        if (rentalMode) {
            addRow(fields, row++, driverLabel, driverBox);
        }
        addRow(fields, row++, rentalMode ? "Scheduled start" : "Lease start",
                startField);
        addRow(fields, row++, rentalMode ? "Expected return" : "Lease end", endField);
        if (!rentalMode) {
            addRow(fields, row++, "Deposit", depositField);
        }
        if (rentalMode) {
            addRow(fields, row++, odometerLabel, odometerField);
            addRow(fields, row++, conditionLabel, conditionField);
        }
        JPanel buttons = new JPanel();
        JButton saveButton = new JButton(rentalMode ? "Create Rental" : "Schedule Lease");
        JButton cancelButton = new JButton("Cancel");
        buttons.add(saveButton);
        buttons.add(cancelButton);
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                saveContract();
            }
        });
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                dispose();
            }
        });
        JPanel bottom = new JPanel(new BorderLayout(5, 5));
        JPanel information = new JPanel(new GridBagLayout());
        addRow(information, 0, "", estimateLabel);
        addRow(information, 1, "", messageLabel);
        bottom.add(information, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.SOUTH);
        add(fields, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        Theme.applyDialogPadding(this);
        getRootPane().setDefaultButton(saveButton);
        installEscapeAction();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(getOwner());
    }

    private void installListeners() {
        kindBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                updateKindControls();
                scheduleAvailability();
            }
        });
        customerBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                scheduleAvailability();
            }
        });
        vehicleBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                updateEstimate();
            }
        });
        attachAvailabilityListener(startField);
        attachAvailabilityListener(endField);
    }

    private void attachAvailabilityListener(JTextField field) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                scheduleAvailability();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                scheduleAvailability();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                scheduleAvailability();
            }
        });
    }

    private void loadLookups() {
        lookupTask.execute(this, new BackgroundTask.Work<LookupData>() {
            @Override
            public LookupData run() throws LfrlmsException {
                return new LookupData(customerService.search(null, null, null,
                        "lastName", true, 1, 500),
                        driverService.listPage("lastName", true, 1, 500));
            }
        }, new BackgroundTask.Completion<LookupData>() {
            @Override
            public void completed(LookupData data, Exception failure) {
                if (failure != null) {
                    messageLabel.setForeground(NEUTRAL_COLOR);
                    messageLabel.setText(" ");
                    GuiSupport.showFailure(ContractFormDialog.this, "Load rental choices",
                            failure, new Runnable() {
                                @Override
                                public void run() {
                                    loadLookups();
                                }
                            });
                    return;
                }
                replaceChoices(data);
                lookupsReady = true;
                scheduleAvailability();
            }
        });
    }

    private void replaceChoices(LookupData data) {
        customerBox.removeAllItems();
        for (Customer customer : data.customers) {
            customerBox.addItem(new CustomerChoice(customer));
        }
        driverBox.removeAllItems();
        driverBox.addItem(new DriverChoice(null));
        for (Driver driver : data.drivers) {
            if (Constants.DRIVER_ACTIVE.equals(driver.getStatus())) {
                driverBox.addItem(new DriverChoice(driver));
            }
        }
    }

    private void scheduleAvailability() {
        if (lookupsReady) {
            availabilityTimer.restart();
        }
    }

    private void refreshAvailability() {
        final Period period;
        try {
            period = readPeriod();
        } catch (RuntimeException exception) {
            messageLabel.setForeground(ERROR_COLOR);
            messageLabel.setText(exception.getMessage());
            vehicleBox.removeAllItems();
            updateEstimate();
            return;
        }
        messageLabel.setForeground(NEUTRAL_COLOR);
        messageLabel.setText("Checking vehicle availability...");
        availabilityTask.execute(this,
                new BackgroundTask.Work<ArrayList<HashMap<String, Object>>>() {
                    @Override
                    public ArrayList<HashMap<String, Object>> run() throws LfrlmsException {
                        return availabilityService.searchAvailableVehicles(period.from,
                                period.to, null, null);
                    }
                }, new BackgroundTask.Completion<ArrayList<HashMap<String, Object>>>() {
                    @Override
                    public void completed(ArrayList<HashMap<String, Object>> rows,
                            Exception failure) {
                        if (failure != null) {
                            messageLabel.setForeground(NEUTRAL_COLOR);
                            messageLabel.setText(" ");
                            GuiSupport.showFailure(ContractFormDialog.this,
                                    "Check vehicle availability", failure, new Runnable() {
                                        @Override
                                        public void run() {
                                            refreshAvailability();
                                        }
                                    });
                            return;
                        }
                        replaceVehicles(rows);
                    }
                });
    }

    private void replaceVehicles(ArrayList<HashMap<String, Object>> rows) {
        Integer previousId = selectedVehicleId();
        vehicleBox.removeAllItems();
        for (HashMap<String, Object> row : rows) {
            VehicleChoice choice = new VehicleChoice(row, rentalMode);
            if (preselectedVehicleId == null
                    || preselectedVehicleId.intValue() == choice.vehicleId) {
                vehicleBox.addItem(choice);
            }
        }
        selectVehicle(previousId);
        if (vehicleBox.getSelectedItem() == null) {
            messageLabel.setForeground(ERROR_COLOR);
            messageLabel.setText(preselectedVehicleId == null
                    ? "No vehicles are available for this period."
                    : "The selected vehicle is unavailable for this period.");
        } else {
            messageLabel.setForeground(SUCCESS_COLOR);
            messageLabel.setText("Availability checked. The service rechecks before saving.");
        }
        updateEstimate();
    }

    private void selectVehicle(Integer vehicleId) {
        int wanted = vehicleId == null && preselectedVehicleId != null
                ? preselectedVehicleId.intValue()
                : (vehicleId == null ? -1 : vehicleId.intValue());
        for (int index = 0; index < vehicleBox.getItemCount(); index++) {
            if (vehicleBox.getItemAt(index).vehicleId == wanted) {
                vehicleBox.setSelectedIndex(index);
                return;
            }
        }
        if (vehicleBox.getItemCount() > 0) {
            vehicleBox.setSelectedIndex(0);
        }
    }

    private void updateEstimate() {
        if (!rentalMode) {
            estimateLabel.setText("Lease rate is applied by the service when scheduled.");
            return;
        }
        VehicleChoice vehicle = (VehicleChoice) vehicleBox.getSelectedItem();
        if (vehicle == null || vehicle.rate == null) {
            estimateLabel.setText("Estimated charge: unavailable for this vehicle.");
            return;
        }
        try {
            Period period = readPeriod();
            BigDecimal estimate = billingService.estimateRental(period.from, period.to,
                    vehicle.rate);
            estimateLabel.setText("Estimated charge: " + estimate.toPlainString());
        } catch (Exception exception) {
            estimateLabel.setText("Estimated charge: enter a valid time range.");
        }
    }

    private void updateKindControls() {
        boolean walkIn = rentalMode && "Walk-in".equals(kindBox.getSelectedItem());
        odometerField.setEnabled(walkIn);
        conditionField.setEnabled(walkIn);
        startField.setEnabled(!walkIn);
        if (walkIn) {
            startField.setText(INPUT_DATE_TIME.format(LocalDateTime.now()));
        }
    }

    private Period readPeriod() {
        if (rentalMode) {
            boolean walkIn = "Walk-in".equals(kindBox.getSelectedItem());
            LocalDateTime from = walkIn ? LocalDateTime.now()
                    : parseInputDateTime(startField.getText());
            LocalDateTime to = parseInputDateTime(endField.getText());
            return new Period(from, to);
        }
        LocalDate fromDate = parseInputDate(startField.getText());
        LocalDate toDate = parseInputDate(endField.getText());
        return new Period(fromDate.atStartOfDay(), toDate.plusDays(1).atStartOfDay());
    }

    private LocalDate parseInputDate(String value) {
        String text = value == null ? "" : value.trim();
        try {
            LocalDate parsed = DateUtil.parseDate(text);
            if (parsed == null) {
                throw new IllegalArgumentException("Enter lease dates in yyyy-MM-dd format.");
            }
            return parsed;
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("Use yyyy-MM-dd for lease dates.");
        }
    }

    private LocalDateTime parseInputDateTime(String value) {
        String text = value == null ? "" : value.trim();
        try {
            LocalDateTime parsed = DateUtil.parseDateTime(text.replace(' ', 'T'));
            if (parsed == null) {
                throw new IllegalArgumentException("Enter rental dates in yyyy-MM-dd HH:mm format.");
            }
            return parsed;
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("Use yyyy-MM-dd HH:mm for rental dates.");
        }
    }

    private void saveContract() {
        final Object customerItem = customerBox.getSelectedItem();
        final VehicleChoice vehicle = (VehicleChoice) vehicleBox.getSelectedItem();
        final DriverChoice driver = (DriverChoice) driverBox.getSelectedItem();
        final Period period;
        final LocalDate leaseStartDate;
        final LocalDate leaseEndDate;
        try {
            period = readPeriod();
            leaseStartDate = rentalMode ? null : parseInputDate(startField.getText());
            leaseEndDate = rentalMode ? null : parseInputDate(endField.getText());
        } catch (RuntimeException exception) {
            messageLabel.setForeground(ERROR_COLOR);
            messageLabel.setText(rentalMode
                    ? "Use yyyy-MM-dd HH:mm for rental dates."
                    : "Use yyyy-MM-dd for lease dates.");
            return;
        }
        final BigDecimal deposit;
        if (rentalMode) {
            deposit = null;
        } else {
            try {
                deposit = new BigDecimal(depositField.getText().trim());
            } catch (NumberFormatException exception) {
                messageLabel.setForeground(ERROR_COLOR);
                messageLabel.setText("Enter a valid non-negative deposit.");
                return;
            }
            if (deposit.signum() < 0) {
                messageLabel.setForeground(ERROR_COLOR);
                messageLabel.setText("Deposit must be zero or greater.");
                return;
            }
        }
        if (customerItem == null || vehicle == null
                || (rentalMode && "Walk-in".equals(kindBox.getSelectedItem())
                && (odometerField.getText().trim().isEmpty()
                || conditionField.getText().trim().isEmpty()))) {
            messageLabel.setForeground(ERROR_COLOR);
            messageLabel.setText("Complete the customer, vehicle and pickup details.");
            return;
        }
        final int customerId = ((CustomerChoice) customerItem).customer.getCustomerId();
        final int vehicleId = vehicle.vehicleId;
        final Integer driverId = driver == null || driver.driver == null
                ? null : Integer.valueOf(driver.driver.getDriverId());
        final boolean walkIn = rentalMode && "Walk-in".equals(kindBox.getSelectedItem());
        final Integer odometer = parseOdometer(walkIn);
        if (walkIn && odometer == null) {
            messageLabel.setForeground(ERROR_COLOR);
            messageLabel.setText("Enter a non-negative odometer reading.");
            return;
        }
        final String condition = conditionField.getText().trim();
        messageLabel.setForeground(NEUTRAL_COLOR);
        messageLabel.setText("Saving...");
        saveTask.execute(this, new BackgroundTask.Work<Integer>() {
            @Override
            public Integer run() throws LfrlmsException {
                if (rentalMode) {
                    if (walkIn) {
                        return Integer.valueOf(rentalService.createWalkIn(customerId,
                                vehicleId, driverId, period.to, odometer.intValue(), condition));
                    }
                    return Integer.valueOf(rentalService.createReservation(customerId,
                            vehicleId, driverId, period.from, period.to));
                }
                return Integer.valueOf(leaseService.scheduleLease(customerId, vehicleId,
                        leaseStartDate, leaseEndDate, deposit));
            }
        }, new BackgroundTask.Completion<Integer>() {
            @Override
            public void completed(Integer id, Exception failure) {
                if (failure != null) {
                    messageLabel.setForeground(NEUTRAL_COLOR);
                    messageLabel.setText(" ");
                    GuiSupport.showFailure(ContractFormDialog.this,
                            rentalMode ? "Create rental" : "Schedule lease", failure, null);
                    return;
                }
                saved = true;
                messageLabel.setForeground(SUCCESS_COLOR);
                messageLabel.setText((rentalMode ? "Rental #" : "Lease #") + id + " saved.");
                dispose();
            }
        });
    }

    private Integer parseOdometer(boolean walkIn) {
        if (!walkIn) {
            return null;
        }
        try {
            int value = Integer.parseInt(odometerField.getText().trim());
            return value < 0 ? null : Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer selectedVehicleId() {
        VehicleChoice choice = (VehicleChoice) vehicleBox.getSelectedItem();
        return choice == null ? null : Integer.valueOf(choice.vehicleId);
    }

    private void addRow(JPanel panel, int row, String title, JComponent field) {
        addRow(panel, row, new JLabel(title == null || title.trim().isEmpty()
                ? "" : title + ":"), field);
    }

    private void addRow(JPanel panel, int row, JLabel title, JComponent field) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.anchor = GridBagConstraints.LINE_END;
        left.insets = new Insets(5, 8, 5, 8);
        panel.add(title, left);
        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row;
        right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(5, 4, 5, 8);
        panel.add(field, right);
    }

    private void installEscapeAction() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "close-dialog");
        getRootPane().getActionMap().put("close-dialog", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                dispose();
            }
        });
    }

    private static class CustomerChoice {
        private final Customer customer;

        private CustomerChoice(Customer customer) {
            this.customer = customer;
        }

        @Override
        public String toString() {
            return customer.getDisplayName() + " (#" + customer.getCustomerId() + ")";
        }
    }

    private static class DriverChoice {
        private final Driver driver;

        private DriverChoice(Driver driver) {
            this.driver = driver;
        }

        @Override
        public String toString() {
            return driver == null ? "No chauffeur" : driver.getFirstName() + " "
                    + driver.getLastName() + " (#" + driver.getDriverId() + ")";
        }
    }

    private static class VehicleChoice {
        private final int vehicleId;
        private final String registrationNo;
        private final String model;
        private final BigDecimal rate;

        private VehicleChoice(HashMap<String, Object> row, boolean rentalMode) {
            vehicleId = ((Number) row.get("vehicle_id")).intValue();
            registrationNo = String.valueOf(row.get("registration_no"));
            model = String.valueOf(row.get("model"));
            Object rateValue = row.get(rentalMode ? "rental_rate" : "lease_rate");
            rate = rateValue instanceof BigDecimal ? (BigDecimal) rateValue : null;
        }

        @Override
        public String toString() {
            return registrationNo + " — " + model + " — rate "
                    + (rate == null ? "unavailable" : rate.toPlainString());
        }
    }

    private static class LookupData {
        private final ArrayList<Customer> customers;
        private final ArrayList<Driver> drivers;

        private LookupData(ArrayList<Customer> customers, ArrayList<Driver> drivers) {
            this.customers = customers;
            this.drivers = drivers;
        }
    }

    private static class Period {
        private final LocalDateTime from;
        private final LocalDateTime to;

        private Period(LocalDateTime from, LocalDateTime to) {
            this.from = from;
            this.to = to;
        }
    }
}
