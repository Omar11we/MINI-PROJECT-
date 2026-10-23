package lfrlms.gui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDate;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import lfrlms.model.CorporateCustomer;
import lfrlms.model.Customer;
import lfrlms.model.IndividualCustomer;
import lfrlms.util.Constants;
import lfrlms.util.DateUtil;
import lfrlms.util.Validator;

/**
 * Purpose: Edits common contact fields and the selected customer subtype.
 * Concept: Polymorphism, CardLayout and copy constructors.
 * Layer: GUI (presentation).
 * Rubric: GUI, inner/anonymous classes.
 */
public class CustomerFormDialog extends JDialog {
    private final Customer original;
    private final JTextField firstNameField;
    private final JTextField lastNameField;
    private final JTextField emailField;
    private final JTextField phoneField;
    private final JTextField streetField;
    private final JTextField cityField;
    private final JTextField pincodeField;
    private final JTextField birthDateField;
    private final JTextField licenseField;
    private final JTextField licenseExpiryField;
    private final JTextField companyField;
    private final JTextField gstField;
    private final JComboBox<String> typeBox;
    private final JPanel subtypeCards;
    private final JLabel messageLabel;
    private Customer result;

    public CustomerFormDialog(Window owner, Customer customer) {
        super(owner, customer == null ? "Add Customer" : "Edit Customer",
                Dialog.ModalityType.APPLICATION_MODAL);
        original = copyCustomer(customer);
        firstNameField = new JTextField(customer == null ? "" : customer.getFirstName(), 24);
        lastNameField = new JTextField(customer == null ? "" : customer.getLastName(), 24);
        emailField = new JTextField(customer == null ? "" : customer.getEmail(), 24);
        phoneField = new JTextField(customer == null ? "" : customer.getPhone(), 24);
        streetField = new JTextField(customer == null ? "" : customer.getStreet(), 24);
        cityField = new JTextField(customer == null ? "" : customer.getCity(), 24);
        pincodeField = new JTextField(customer == null ? "" : customer.getPincode(), 24);
        IndividualCustomer individual = customer instanceof IndividualCustomer
                ? (IndividualCustomer) customer : null;
        CorporateCustomer corporate = customer instanceof CorporateCustomer
                ? (CorporateCustomer) customer : null;
        birthDateField = new JTextField(individual == null ? "" :
                DateUtil.formatDate(individual.getDateOfBirth()), 24);
        licenseField = new JTextField(individual == null ? "" : individual.getLicenseNo(), 24);
        licenseExpiryField = new JTextField(individual == null ? "" :
                DateUtil.formatDate(individual.getLicenseExpiry()), 24);
        companyField = new JTextField(corporate == null ? "" : corporate.getCompanyName(), 24);
        gstField = new JTextField(corporate == null ? "" : corporate.getGstNo(), 24);
        typeBox = new JComboBox<String>(new String[] {
            Constants.CUSTOMER_INDIVIDUAL, Constants.CUSTOMER_CORPORATE});
        if (customer instanceof CorporateCustomer) {
            typeBox.setSelectedItem(Constants.CUSTOMER_CORPORATE);
        }
        subtypeCards = buildSubtypeCards();
        messageLabel = new JLabel(" ");
        buildForm();
    }

    public static Customer showDialog(Window owner, Customer customer) {
        CustomerFormDialog dialog = new CustomerFormDialog(owner, customer);
        dialog.setVisible(true);
        Customer value = dialog.result;
        dialog.dispose();
        return value;
    }

    private void buildForm() {
        JPanel common = new JPanel(new GridBagLayout());
        addRow(common, 0, "Type", typeBox);
        addRow(common, 1, "First name / contact", firstNameField);
        addRow(common, 2, "Last name", lastNameField);
        addRow(common, 3, "Email", emailField);
        addRow(common, 4, "Phone (10 digits)", phoneField);
        addRow(common, 5, "Street", streetField);
        addRow(common, 6, "City", cityField);
        addRow(common, 7, "Pincode (6 digits)", pincodeField);
        typeBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                CardLayout cards = (CardLayout) subtypeCards.getLayout();
                cards.show(subtypeCards, (String) typeBox.getSelectedItem());
            }
        });
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");
        JPanel buttons = new JPanel();
        buttons.add(saveButton);
        buttons.add(cancelButton);
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                saveCustomer();
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
        JPanel center = new JPanel(new BorderLayout());
        center.add(common, BorderLayout.NORTH);
        center.add(subtypeCards, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
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

    private JPanel buildSubtypeCards() {
        JPanel cards = new JPanel(new CardLayout());
        JPanel individual = new JPanel(new GridBagLayout());
        addRow(individual, 0, "Date of birth (YYYY-MM-DD)", birthDateField);
        addRow(individual, 1, "Driving licence number", licenseField);
        addRow(individual, 2, "Licence expiry (YYYY-MM-DD)", licenseExpiryField);
        JPanel corporate = new JPanel(new GridBagLayout());
        addRow(corporate, 0, "Company name", companyField);
        addRow(corporate, 1, "GST number", gstField);
        cards.add(individual, Constants.CUSTOMER_INDIVIDUAL);
        cards.add(corporate, Constants.CUSTOMER_CORPORATE);
        return cards;
    }

    private void saveCustomer() {
        String firstName = firstNameField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();
        String street = streetField.getText().trim();
        String city = cityField.getText().trim();
        String pincode = pincodeField.getText().trim();
        if (!Validator.isValid(firstName) || !Validator.isValid(lastName)
                || !Validator.isValidEmail(email) || !Validator.isValidPhone(phone)
                || !Validator.isValid(street) || !Validator.isValid(city)
                || !Validator.isValidPincode(pincode)) {
            messageLabel.setText("Check the contact name, email, phone, address and pincode.");
            return;
        }
        if (Constants.CUSTOMER_INDIVIDUAL.equals(typeBox.getSelectedItem())) {
            saveIndividual(firstName, lastName, email, phone, street, city, pincode);
        } else {
            saveCorporate(firstName, lastName, email, phone, street, city, pincode);
        }
    }

    private void saveIndividual(String firstName, String lastName, String email,
            String phone, String street, String city, String pincode) {
        try {
            LocalDate birth = DateUtil.parseDate(birthDateField.getText());
            LocalDate expiry = DateUtil.parseDate(licenseExpiryField.getText());
            String license = licenseField.getText().trim().toUpperCase();
            if (birth == null || expiry == null || !Validator.isValidLicenseNumber(license)) {
                messageLabel.setText("Enter valid birth date, licence number and expiry date.");
                return;
            }
            IndividualCustomer individual = original instanceof IndividualCustomer
                    ? new IndividualCustomer((IndividualCustomer) original)
                    : new IndividualCustomer();
            if (original != null) {
                individual.setCustomerId(original.getCustomerId());
            }
            setCommon(individual, firstName, lastName, email, phone, street, city, pincode);
            individual.setDateOfBirth(birth);
            individual.setLicenseNo(license);
            individual.setLicenseExpiry(expiry);
            result = individual;
            dispose();
        } catch (java.time.format.DateTimeParseException exception) {
            messageLabel.setText("Use the date format YYYY-MM-DD.");
        }
    }

    private void saveCorporate(String firstName, String lastName, String email,
            String phone, String street, String city, String pincode) {
        String company = companyField.getText().trim();
        String gst = gstField.getText().trim().toUpperCase();
        if (!Validator.isValid(company) || !Validator.isValidGstNo(gst)) {
            messageLabel.setText("Enter a company name and a valid GST number.");
            return;
        }
        CorporateCustomer corporate = original instanceof CorporateCustomer
                ? new CorporateCustomer((CorporateCustomer) original)
                : new CorporateCustomer();
        if (original != null) {
            corporate.setCustomerId(original.getCustomerId());
        }
        setCommon(corporate, firstName, lastName, email, phone, street, city, pincode);
        corporate.setCompanyName(company);
        corporate.setGstNo(gst);
        result = corporate;
        dispose();
    }

    private void setCommon(Customer customer, String firstName, String lastName,
            String email, String phone, String street, String city, String pincode) {
        customer.setFirstName(firstName);
        customer.setLastName(lastName);
        customer.setEmail(email);
        customer.setPhone(phone);
        customer.setStreet(street);
        customer.setCity(city);
        customer.setPincode(pincode);
    }

    private Customer copyCustomer(Customer customer) {
        if (customer instanceof IndividualCustomer) {
            return new IndividualCustomer((IndividualCustomer) customer);
        }
        if (customer instanceof CorporateCustomer) {
            return new CorporateCustomer((CorporateCustomer) customer);
        }
        return null;
    }

    private void addRow(JPanel panel, int row, String label, JComponent field) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.anchor = GridBagConstraints.LINE_END;
        left.insets = new Insets(4, 8, 4, 8);
        panel.add(new JLabel(label + ":"), left);
        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row;
        right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(4, 4, 4, 8);
        panel.add(field, right);
    }
}
