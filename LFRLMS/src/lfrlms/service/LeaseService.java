package lfrlms.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import lfrlms.dao.CustomerDao;
import lfrlms.dao.LeaseDao;
import lfrlms.dao.VehicleDao;
import lfrlms.exception.IneligibleCustomerException;
import lfrlms.exception.LfrlmsException;
import lfrlms.exception.ValidationException;
import lfrlms.exception.VehicleNotAvailableException;
import lfrlms.model.Customer;
import lfrlms.model.IndividualCustomer;
import lfrlms.model.Lease;
import lfrlms.model.Vehicle;
import lfrlms.util.Constants;
import lfrlms.util.Validator;

// Layer: Service (business logic).
// Rubric: JDBC CRUD, Exception handling.
/** Coordinates lease reservations, starts and terminal transitions. */
public class LeaseService extends AbstractService {
    private final LeaseDao leaseDao;
    private final VehicleDao vehicleDao;
    private final CustomerDao customerDao;
    private final AvailabilityService availabilityService;

    public LeaseService() {
        this(new LeaseDao(), new VehicleDao(), new CustomerDao(), new AvailabilityService());
    }

    public LeaseService(LeaseDao leaseDao, VehicleDao vehicleDao,
            CustomerDao customerDao, AvailabilityService availabilityService) {
        this.leaseDao = leaseDao;
        this.vehicleDao = vehicleDao;
        this.customerDao = customerDao;
        this.availabilityService = availabilityService;
    }

    /** Creates a scheduled lease after locking the vehicle and checking availability. */
    public int createLease(Lease lease) throws LfrlmsException {
        // Validate input
        if (lease == null || lease.getCustomerId() <= 0 || lease.getVehicleId() <= 0
                || !Validator.isValid(lease.getStartDate(), lease.getEndDate())
                || (lease.getDeposit() != null && !Validator.isNonNegative(lease.getDeposit()))
                || !Constants.LEASE_SCHEDULED.equals(lease.getStatus())) {
            throw new ValidationException("Enter a valid scheduled lease period.");
        }
        // Open a connection and start a transaction
        try (Connection connection = openConnection()) {
            beginTransaction(connection);
            try {
                // Lock the vehicle row (SELECT ... FOR UPDATE) so two bookings
                // cannot both pass the availability check
                Vehicle vehicle = vehicleDao.lockByIdForUpdate(connection, lease.getVehicleId());
                // The vehicle must exist, have a lease rate and be in service
                if (vehicle == null || vehicle.getLeaseRate() == null
                        || Constants.VEHICLE_MAINTENANCE.equals(vehicle.getStatus())
                        || Constants.VEHICLE_INACTIVE.equals(vehicle.getStatus())) {
                    throw new VehicleNotAvailableException("The selected vehicle cannot be leased.");
                }
                // Check customer eligibility
                validateCustomer(lease);
                // Recheck availability for whole days, from the start date through the end date
                if (!availabilityService.isVehicleAvailable(lease.getVehicleId(),
                        lease.getStartDate().atStartOfDay(),
                        lease.getEndDate().plusDays(1).atStartOfDay(), null, null)) {
                    throw new VehicleNotAvailableException(
                            "The vehicle is not available for the full lease period.");
                }
                // Fix the current lease rate on the lease and insert it
                lease.setMonthlyCharge(vehicle.getLeaseRate());
                int leaseId = leaseDao.insert(connection, lease);
                lease.setLeaseId(leaseId);
                // Commit; the catch blocks below roll back on any failure
                commit(connection);
                return leaseId;
            } catch (LfrlmsException exception) {
                rollback(connection, exception);
                throw exception;
            } catch (RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw databaseFailure("close the lease transaction", exception);
        }
    }

    /** Builds a scheduled lease from form values and creates it. */
    public int scheduleLease(int customerId, int vehicleId, LocalDate startDate,
            LocalDate endDate, BigDecimal deposit) throws LfrlmsException {
        if (deposit == null || !Validator.isNonNegative(deposit)) {
            throw new ValidationException("Lease deposit must be zero or greater.");
        }
        Lease lease = new Lease();
        lease.setCustomerId(customerId);
        lease.setVehicleId(vehicleId);
        lease.setStartDate(startDate);
        lease.setEndDate(endDate);
        lease.setDeposit(deposit);
        lease.setStatus(Constants.LEASE_SCHEDULED);
        return createLease(lease);
    }

    /** Starts a scheduled lease and marks its vehicle Leased in one transaction. */
    public void startLease(int leaseId) throws LfrlmsException {
        // Read the lease once to find which vehicle to lock
        Lease initial = leaseDao.findById(leaseId);
        if (initial == null) {
            throw new ValidationException("The lease record no longer exists.");
        }
        // Open a connection and start a transaction
        try (Connection connection = openConnection()) {
            beginTransaction(connection);
            try {
                // Lock the vehicle row (SELECT ... FOR UPDATE) so no other
                // booking can change it until we commit
                Vehicle vehicle = vehicleDao.lockByIdForUpdate(connection, initial.getVehicleId());
                // Read the lease again now that the vehicle is locked, in case it changed
                Lease lease = leaseDao.findById(leaseId);
                // Recheck that the lease is still Scheduled
                if (vehicle == null || lease == null
                        || !Constants.LEASE_SCHEDULED.equals(lease.getStatus())) {
                    throw new ValidationException("Only a scheduled lease can be started.");
                }
                // Check customer eligibility
                validateCustomer(lease);
                // Start the check at now or at the scheduled start, whichever is earlier
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime scheduledStart = lease.getStartDate().atStartOfDay();
                LocalDateTime availabilityFrom = now.isBefore(scheduledStart)
                        ? now : scheduledStart;
                // Recheck availability, ignoring this lease's own booking
                if (!availabilityService.isVehicleAvailable(lease.getVehicleId(),
                        availabilityFrom,
                        lease.getEndDate().plusDays(1).atStartOfDay(), null,
                        Integer.valueOf(leaseId))) {
                    throw new VehicleNotAvailableException(
                            "The vehicle has another commitment during this lease.");
                }
                // Mark the lease Active and the vehicle Leased
                lease.setStatus(Constants.LEASE_ACTIVE);
                vehicle.setStatus(Constants.VEHICLE_LEASED);
                if (!leaseDao.update(connection, lease) || !vehicleDao.update(connection, vehicle)) {
                    throw new ValidationException("The lease could not be started.");
                }
                // Commit; the catch blocks below roll back on any failure
                commit(connection);
            } catch (LfrlmsException exception) {
                rollback(connection, exception);
                throw exception;
            } catch (RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw databaseFailure("close the lease transaction", exception);
        }
    }

    /** Completes or terminates a lease, freeing the vehicle if the lease had started. */
    public void finishLease(int leaseId, String finalStatus) throws LfrlmsException {
        // Validate input
        if (!Constants.LEASE_COMPLETED.equals(finalStatus)
                && !Constants.LEASE_TERMINATED.equals(finalStatus)) {
            throw new ValidationException("Choose Completed or Terminated as the final status.");
        }
        // Only an Active or Scheduled lease can be ended
        Lease initial = leaseDao.findById(leaseId);
        if (initial == null || (!Constants.LEASE_ACTIVE.equals(initial.getStatus())
                && !Constants.LEASE_SCHEDULED.equals(initial.getStatus()))) {
            throw new ValidationException("Only an active or scheduled lease can be ended.");
        }
        // Open a connection and start a transaction
        try (Connection connection = openConnection()) {
            beginTransaction(connection);
            try {
                // Lock the vehicle row (SELECT ... FOR UPDATE) so no other
                // booking can change it until we commit
                Vehicle vehicle = vehicleDao.lockByIdForUpdate(connection, initial.getVehicleId());
                // Read the lease again now that the vehicle is locked
                Lease lease = leaseDao.findById(leaseId);
                if (lease == null) {
                    throw new ValidationException("The lease record no longer exists.");
                }
                // Remember whether the lease had started, then save the final status
                boolean wasActive = Constants.LEASE_ACTIVE.equals(lease.getStatus());
                lease.setStatus(finalStatus);
                if (!leaseDao.update(connection, lease)) {
                    throw new ValidationException("The lease could not be updated.");
                }
                // Free the vehicle only if the lease had started
                if (wasActive && vehicle != null) {
                    vehicle.setStatus(Constants.VEHICLE_AVAILABLE);
                    vehicleDao.update(connection, vehicle);
                }
                // Commit; the catch blocks below roll back on any failure
                commit(connection);
            } catch (LfrlmsException exception) {
                rollback(connection, exception);
                throw exception;
            } catch (RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw databaseFailure("close the lease transaction", exception);
        }
    }

    /** Finds a lease by id, or returns null. */
    public Lease findById(int leaseId) throws LfrlmsException {
        return leaseDao.findById(leaseId);
    }

    /** Searches leases one page at a time. */
    public ArrayList<Lease> search(String text, String status,
            LocalDate from, LocalDate to, boolean outstandingOnly,
            String sortKey, boolean ascending, int pageNumber, int pageSize)
            throws LfrlmsException {
        return leaseDao.search(text, status, from, to, outstandingOnly,
                sortKey, ascending, pageNumber, pageSize);
    }

    /** Counts leases that match the search filters. */
    public long countSearchResults(String text, String status, LocalDate from,
            LocalDate to, boolean outstandingOnly) throws LfrlmsException {
        return leaseDao.countSearchResults(text, status, from, to, outstandingOnly);
    }

    private void validateCustomer(Lease lease) throws LfrlmsException {
        Customer customer = customerDao.findById(lease.getCustomerId());
        if (customer == null) {
            throw new IneligibleCustomerException("Select an existing customer.");
        }
        if (customer instanceof IndividualCustomer
                && !Validator.isEligibleThrough(
                ((IndividualCustomer) customer).getLicenseExpiry(), lease.getEndDate())) {
            throw new IneligibleCustomerException(
                    "The individual's licence must remain valid through the lease end date.");
        }
    }
}
