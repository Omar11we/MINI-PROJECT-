package lfrlms.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import lfrlms.dao.LeaseDao;
import lfrlms.dao.PaymentDao;
import lfrlms.dao.RentalDao;
import lfrlms.dao.ReportDao;
import lfrlms.exception.LfrlmsException;
import lfrlms.exception.ValidationException;
import lfrlms.model.Lease;
import lfrlms.model.Payment;
import lfrlms.model.Rental;
import lfrlms.util.Constants;
import lfrlms.util.Validator;

// Layer: Service (business logic).
// Rubric: JDBC CRUD, Exception handling.
/** Applies payment reference, positive-amount and outstanding-balance rules. */
public class PaymentService extends AbstractService {
    private static final int PAGE_SIZE = 100;
    private final PaymentDao paymentDao;
    private final RentalDao rentalDao;
    private final LeaseDao leaseDao;
    private final ReportDao reportDao;

    public PaymentService() {
        this(new PaymentDao(), new RentalDao(), new LeaseDao(), new ReportDao());
    }

    public PaymentService(PaymentDao paymentDao, RentalDao rentalDao,
            LeaseDao leaseDao, ReportDao reportDao) {
        this.paymentDao = paymentDao;
        this.rentalDao = rentalDao;
        this.leaseDao = leaseDao;
        this.reportDao = reportDao;
    }

    /** Records a payment after locking its rental or lease and checking the balance. */
    public int recordPayment(Payment payment) throws LfrlmsException {
        validateShape(payment);
        try (Connection connection = openConnection()) {
            beginTransaction(connection);
            try {
                // Lock the rental or lease row (SELECT ... FOR UPDATE) so two
                // payments cannot both pass the balance check
                if (payment.getRentalId() != null) {
                    if (rentalDao.lockByIdForUpdate(connection,
                            payment.getRentalId().intValue()) == null) {
                        throw new ValidationException("Select an existing rental.");
                    }
                } else if (leaseDao.lockByIdForUpdate(connection,
                        payment.getLeaseId().intValue()) == null) {
                    throw new ValidationException("Select an existing lease.");
                }
                validateOutstanding(payment, null);
                int paymentId = paymentDao.insert(connection, payment);
                commit(connection);
                return paymentId;
            } catch (LfrlmsException exception) {
                rollback(connection, exception);
                throw exception;
            } catch (RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw databaseFailure("close the payment transaction", exception);
        }
    }

    /** Returns a customer's outstanding balance from the database function. */
    public BigDecimal customerOutstanding(int customerId) throws LfrlmsException {
        return reportDao.customerOutstanding(customerId);
    }

    /** Searches payments one page at a time. */
    public ArrayList<Payment> search(String text, String status, LocalDateTime from,
            LocalDateTime to, boolean outstandingOnly, String sortKey,
            boolean ascending, int pageNumber, int pageSize) throws LfrlmsException {
        return paymentDao.search(text, status, from, to, outstandingOnly,
                sortKey, ascending, pageNumber, pageSize);
    }

    /** Counts payments that match the search filters. */
    public long countSearchResults(String text, String status, LocalDateTime from,
            LocalDateTime to, boolean outstandingOnly) throws LfrlmsException {
        return paymentDao.countSearchResults(text, status, from, to, outstandingOnly);
    }

    /** Lists active and completed rentals that can take payments. */
    public ArrayList<Rental> listBillableRentals() throws LfrlmsException {
        ArrayList<Rental> result = new ArrayList<Rental>();
        appendRentalsByStatus(result, Constants.RENTAL_ACTIVE);
        appendRentalsByStatus(result, Constants.RENTAL_COMPLETED);
        return result;
    }

    /** Lists active and completed leases that can take payments. */
    public ArrayList<Lease> listBillableLeases() throws LfrlmsException {
        ArrayList<Lease> result = new ArrayList<Lease>();
        appendLeasesByStatus(result, Constants.LEASE_ACTIVE);
        appendLeasesByStatus(result, Constants.LEASE_COMPLETED);
        return result;
    }

    /** Returns the unpaid amount on a rental. */
    public BigDecimal rentalOutstanding(int rentalId) throws LfrlmsException {
        Rental rental = rentalDao.findById(rentalId);
        if (rental == null) {
            throw new ValidationException("Select an existing rental.");
        }
        return outstandingForRental(rental, null);
    }

    /** Returns the unpaid amount on a lease. */
    public BigDecimal leaseOutstanding(int leaseId) throws LfrlmsException {
        Lease lease = leaseDao.findById(leaseId);
        if (lease == null) {
            throw new ValidationException("Select an existing lease.");
        }
        return outstandingForLease(lease, null);
    }

    /** Marks a successful payment as refunded. */
    public void refund(int paymentId) throws LfrlmsException {
        Payment payment = paymentDao.findById(paymentId);
        if (payment == null || Constants.PAYMENT_REFUNDED.equals(payment.getStatus())) {
            throw new ValidationException("Choose an existing, unrefunded payment.");
        }
        if (!Constants.PAYMENT_SUCCESS.equals(payment.getStatus())) {
            throw new ValidationException("Only successful payments can be refunded.");
        }
        payment.setStatus(Constants.PAYMENT_REFUNDED);
        if (!paymentDao.update(payment)) {
            throw new ValidationException("The payment could not be updated.");
        }
    }

    void recordAdvanceForConfirmedRental(Connection connection, Payment payment,
            Rental rental) throws LfrlmsException {
        validateShape(payment);
        BigDecimal due = outstandingForRental(rental, null);
        if (payment.getAmount().compareTo(due) > 0) {
            throw new ValidationException("The advance exceeds the rental's outstanding balance.");
        }
        paymentDao.insert(connection, payment);
    }

    private void validateOutstanding(Payment payment, Integer ignoredPaymentId)
            throws LfrlmsException {
        BigDecimal due;
        if (payment.getRentalId() != null) {
            Rental rental = rentalDao.findById(payment.getRentalId().intValue());
            if (rental == null) {
                throw new ValidationException("Select an existing rental.");
            }
            due = outstandingForRental(rental, ignoredPaymentId);
        } else {
            Lease lease = leaseDao.findById(payment.getLeaseId().intValue());
            if (lease == null) {
                throw new ValidationException("Select an existing lease.");
            }
            due = outstandingForLease(lease, ignoredPaymentId);
        }
        if (payment.getAmount().compareTo(due) > 0) {
            throw new ValidationException("The payment exceeds the outstanding balance.");
        }
    }

    private BigDecimal outstandingForRental(Rental rental, Integer ignoredPaymentId)
            throws LfrlmsException {
        if (!Constants.RENTAL_ACTIVE.equals(rental.getStatus())
                && !Constants.RENTAL_COMPLETED.equals(rental.getStatus())) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal total = rental.calculateCharge();
        return total.subtract(successfulPayments(rental.getRentalId(), null, ignoredPaymentId));
    }

    private BigDecimal outstandingForLease(Lease lease, Integer ignoredPaymentId)
            throws LfrlmsException {
        if (!Constants.LEASE_ACTIVE.equals(lease.getStatus())
                && !Constants.LEASE_COMPLETED.equals(lease.getStatus())) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal total = lease.calculateCharge();
        return total.subtract(successfulPayments(null, lease.getLeaseId(), ignoredPaymentId));
    }

    private BigDecimal successfulPayments(Integer rentalId, Integer leaseId,
            Integer ignoredPaymentId) throws LfrlmsException {
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        HashSet<Integer> seen = new HashSet<Integer>();
        int pageNumber = 1;
        while (true) {
            ArrayList<Payment> page = paymentDao.search(null, null, null, null, false,
                    "paymentDate", true, pageNumber, PAGE_SIZE);
            for (Payment row : page) {
                if (!seen.add(Integer.valueOf(row.getPaymentId()))) {
                    continue;
                }
                boolean sameRental = rentalId != null
                        && rentalId.equals(row.getRentalId());
                boolean sameLease = leaseId != null && leaseId.equals(row.getLeaseId());
                boolean ignored = ignoredPaymentId != null
                        && ignoredPaymentId.intValue() == row.getPaymentId();
                if (!ignored && Constants.PAYMENT_SUCCESS.equals(row.getStatus())
                        && (sameRental || sameLease)) {
                    total = total.add(row.getAmount());
                }
            }
            if (page.size() < PAGE_SIZE) {
                return total;
            }
            pageNumber++;
        }
    }

    private void appendRentalsByStatus(ArrayList<Rental> result, String status)
            throws LfrlmsException {
        int pageNumber = 1;
        while (true) {
            ArrayList<Rental> page = rentalDao.search(null, status, null, null,
                    false, "scheduledStart", true, pageNumber, PAGE_SIZE);
            result.addAll(page);
            if (page.size() < PAGE_SIZE) {
                return;
            }
            pageNumber++;
        }
    }

    private void appendLeasesByStatus(ArrayList<Lease> result, String status)
            throws LfrlmsException {
        int pageNumber = 1;
        while (true) {
            ArrayList<Lease> page = leaseDao.search(null, status, null, null,
                    false, "startDate", true, pageNumber, PAGE_SIZE);
            result.addAll(page);
            if (page.size() < PAGE_SIZE) {
                return;
            }
            pageNumber++;
        }
    }

    private void validateShape(Payment payment) throws ValidationException {
        if (payment == null || !Validator.isPositive(payment.getAmount())) {
            throw new ValidationException("Payment amount must be greater than zero.");
        }
        boolean rentalReference = payment.getRentalId() != null;
        boolean leaseReference = payment.getLeaseId() != null;
        if (rentalReference == leaseReference) {
            throw new ValidationException("Choose exactly one rental or lease reference.");
        }
        if (!isMethod(payment.getMethod()) || !isPaymentStatus(payment.getStatus())) {
            throw new ValidationException("Choose a valid payment method and status.");
        }
        if (payment.getPaymentDate() == null) {
            payment.setPaymentDate(LocalDateTime.now());
        }
    }

    private boolean isMethod(String method) {
        return Constants.PAYMENT_CASH.equals(method) || Constants.PAYMENT_CARD.equals(method)
                || Constants.PAYMENT_BANK_TRANSFER.equals(method)
                || Constants.PAYMENT_UPI.equals(method);
    }

    private boolean isPaymentStatus(String status) {
        return Constants.PAYMENT_SUCCESS.equals(status)
                || Constants.PAYMENT_PENDING.equals(status)
                || Constants.PAYMENT_FAILED.equals(status)
                || Constants.PAYMENT_REFUNDED.equals(status);
    }
}
