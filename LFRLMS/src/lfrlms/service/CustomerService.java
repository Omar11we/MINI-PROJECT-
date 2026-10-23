package lfrlms.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import lfrlms.dao.CorporateCustomerDao;
import lfrlms.dao.CustomerDao;
import lfrlms.dao.IndividualCustomerDao;
import lfrlms.exception.LfrlmsException;
import lfrlms.exception.RecordInUseException;
import lfrlms.exception.ValidationException;
import lfrlms.model.CorporateCustomer;
import lfrlms.model.Customer;
import lfrlms.model.IndividualCustomer;
import lfrlms.util.Validator;

// Layer: Service (business logic).
// Rubric: JDBC CRUD, Exception handling.
/** Saves each customer parent row and its single subtype in one transaction. */
public class CustomerService extends AbstractService {
    public static final String HISTORY_DELETE_MESSAGE =
            "This customer has transaction history and cannot be deleted.";

    private final CustomerDao customerDao;
    private final IndividualCustomerDao individualDao;
    private final CorporateCustomerDao corporateDao;

    public CustomerService() {
        this(new CustomerDao(), new IndividualCustomerDao(), new CorporateCustomerDao());
    }

    public CustomerService(CustomerDao customerDao, IndividualCustomerDao individualDao,
            CorporateCustomerDao corporateDao) {
        this.customerDao = customerDao;
        this.individualDao = individualDao;
        this.corporateDao = corporateDao;
    }

    /** Inserts or updates a customer and its subtype row in one transaction. */
    public int save(Customer customer) throws LfrlmsException {
        // Validate input
        validate(customer);
        // Open a connection and start a transaction
        try (Connection connection = openConnection()) {
            beginTransaction(connection);
            try {
                // A new customer has no id yet
                int customerId = customer.getCustomerId();
                if (customerId <= 0) {
                    // Insert the parent row, then its subtype row
                    customerId = customerDao.insert(connection, customer);
                    customer.setCustomerId(customerId);
                    insertSubtype(connection, customer);
                } else {
                    // Update the parent row, then bring the subtype row in line
                    if (!customerDao.update(connection, customer)) {
                        throw new ValidationException("The customer record no longer exists.");
                    }
                    synchronizeSubtype(connection, customer);
                }
                // Commit; the catch blocks below roll back on any failure
                commit(connection);
                return customerId;
            } catch (LfrlmsException exception) {
                rollback(connection, exception);
                throw exception;
            } catch (RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw databaseFailure("close the customer transaction", exception);
        }
    }

    /** Deletes a customer with no transaction history, subtype row first. */
    public boolean delete(int customerId) throws LfrlmsException {
        // Open a connection and start a transaction
        try (Connection connection = openConnection()) {
            beginTransaction(connection);
            try {
                // Refuse to delete a customer who has rentals or leases
                if (customerDao.hasTransactionHistory(connection, customerId)) {
                    throw new RecordInUseException(HISTORY_DELETE_MESSAGE);
                }
                // Delete the subtype rows first, then the parent row
                individualDao.delete(connection, customerId);
                corporateDao.delete(connection, customerId);
                boolean deleted = customerDao.delete(connection, customerId);
                // Commit; the catch blocks below roll back on any failure
                commit(connection);
                return deleted;
            } catch (RecordInUseException exception) {
                rollback(connection, exception);
                // Also covers a foreign-key error, so staff always see the history message
                throw new RecordInUseException(HISTORY_DELETE_MESSAGE, exception);
            } catch (LfrlmsException exception) {
                rollback(connection, exception);
                throw exception;
            } catch (RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw databaseFailure("close the customer transaction", exception);
        }
    }

    /** Checks the customer exists and has no transaction history. */
    public boolean canDelete(int customerId) throws LfrlmsException {
        if (customerId <= 0 || customerDao.findById(customerId) == null) {
            throw new ValidationException("The customer record no longer exists.");
        }
        return !customerDao.hasTransactionHistory(customerId);
    }

    /** Finds a customer by id, or returns null. */
    public Customer findById(int customerId) throws LfrlmsException {
        return customerDao.findById(customerId);
    }

    /** Searches customers one page at a time. */
    public ArrayList<Customer> search(String text, String customerType,
            Integer licenseExpiryWithinDays, String sortKey, boolean ascending,
            int pageNumber, int pageSize) throws LfrlmsException {
        return customerDao.search(text, customerType, licenseExpiryWithinDays,
                sortKey, ascending, pageNumber, pageSize);
    }

    /** Counts customers that match the search filters. */
    public long countSearchResults(String text, String customerType,
            Integer licenseExpiryWithinDays) throws LfrlmsException {
        return customerDao.countSearchResults(text, customerType, licenseExpiryWithinDays);
    }

    private void synchronizeSubtype(Connection connection, Customer customer)
            throws LfrlmsException {
        if (customer instanceof IndividualCustomer) {
            // Drop any corporate row in case the type changed,
            // then update the individual row, or insert it if missing
            corporateDao.delete(connection, customer.getCustomerId());
            IndividualCustomer individual = (IndividualCustomer) customer;
            if (!individualDao.update(connection, individual)) {
                individualDao.insert(connection, individual);
            }
        } else if (customer instanceof CorporateCustomer) {
            // Drop any individual row in case the type changed,
            // then update the corporate row, or insert it if missing
            individualDao.delete(connection, customer.getCustomerId());
            CorporateCustomer corporate = (CorporateCustomer) customer;
            if (!corporateDao.update(connection, corporate)) {
                corporateDao.insert(connection, corporate);
            }
        }
    }

    private void insertSubtype(Connection connection, Customer customer)
            throws LfrlmsException {
        if (customer instanceof IndividualCustomer) {
            individualDao.insert(connection, (IndividualCustomer) customer);
        } else if (customer instanceof CorporateCustomer) {
            corporateDao.insert(connection, (CorporateCustomer) customer);
        } else {
            throw new ValidationException("Choose an individual or corporate customer.");
        }
    }

    private void validate(Customer customer) throws ValidationException {
        // Check the common contact fields
        if (customer == null || !Validator.isValid(customer.getFirstName())
                || !Validator.isValid(customer.getLastName())
                || !Validator.isValidEmail(customer.getEmail())
                || !Validator.isValidPhone(customer.getPhone())
                || !Validator.isValid(customer.getStreet())
                || !Validator.isValid(customer.getCity())
                || !Validator.isValidPincode(customer.getPincode())) {
            throw new ValidationException("Enter valid customer contact details.");
        }
        // Check the fields required by the subtype
        if (customer instanceof IndividualCustomer) {
            IndividualCustomer individual = (IndividualCustomer) customer;
            if (individual.getDateOfBirth() == null
                    || !Validator.isValidLicenseNumber(individual.getLicenseNo())
                    || individual.getLicenseExpiry() == null) {
                throw new ValidationException("Enter the individual's birth and licence details.");
            }
        } else if (customer instanceof CorporateCustomer) {
            CorporateCustomer corporate = (CorporateCustomer) customer;
            if (!Validator.isValid(corporate.getCompanyName())
                    || !Validator.isValidGstNo(corporate.getGstNo())) {
                throw new ValidationException("Enter a valid company name and GST number.");
            }
        } else {
            throw new ValidationException("Choose an individual or corporate customer.");
        }
    }
}
