package lfrlms.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import lfrlms.dao.CustomerDao;
import lfrlms.dao.DriverDao;
import lfrlms.dao.RentalDao;
import lfrlms.dao.VehicleDao;
import lfrlms.exception.IneligibleCustomerException;
import lfrlms.exception.LfrlmsException;
import lfrlms.exception.ValidationException;
import lfrlms.exception.VehicleNotAvailableException;
import lfrlms.model.CorporateCustomer;
import lfrlms.model.Customer;
import lfrlms.model.Driver;
import lfrlms.model.IndividualCustomer;
import lfrlms.model.Payment;
import lfrlms.model.Rental;
import lfrlms.model.Vehicle;
import lfrlms.util.Constants;
import lfrlms.util.Validator;

// Layer: Service (business logic).
// Rubric: JDBC CRUD, Exception handling.
/** Coordinates rental creation, pickup and return operations. */
public class RentalService extends AbstractService {
    private final RentalDao rentalDao;
    private final VehicleDao vehicleDao;
    private final CustomerDao customerDao;
    private final DriverDao driverDao;
    private final AvailabilityService availabilityService;
    private final PaymentService paymentService;

    public RentalService() {
        this(new RentalDao(), new VehicleDao(), new CustomerDao(), new DriverDao(),
                new AvailabilityService(), new PaymentService());
    }

    public RentalService(RentalDao rentalDao, VehicleDao vehicleDao,
            CustomerDao customerDao, DriverDao driverDao,
            AvailabilityService availabilityService, PaymentService paymentService) {
        this.rentalDao = rentalDao;
        this.vehicleDao = vehicleDao;
        this.customerDao = customerDao;
        this.driverDao = driverDao;
        this.availabilityService = availabilityService;
        this.paymentService = paymentService;
    }

    /** Creates a rental with no advance payment. */
    public int createRental(Rental rental) throws LfrlmsException {
        return createRental(rental, null);
    }

    /** Creates a confirmed reservation for a future period. */
    public int createReservation(int customerId, int vehicleId, Integer driverId,
            LocalDateTime scheduledStart, LocalDateTime expectedReturn)
            throws LfrlmsException {
        Rental rental = new Rental();
        rental.setCustomerId(customerId);
        rental.setVehicleId(vehicleId);
        rental.setDriverId(driverId);
        rental.setScheduledStartDatetime(scheduledStart);
        rental.setExpectedReturnDatetime(expectedReturn);
        rental.setStatus(Constants.RENTAL_CONFIRMED);
        return createRental(rental);
    }

    /** Creates an active walk-in rental that starts now. */
    public int createWalkIn(int customerId, int vehicleId, Integer driverId,
            LocalDateTime expectedReturn, int odometerOut, String conditionOut)
            throws LfrlmsException {
        LocalDateTime pickupTime = LocalDateTime.now();
        Rental rental = new Rental();
        rental.setCustomerId(customerId);
        rental.setVehicleId(vehicleId);
        rental.setDriverId(driverId);
        rental.setScheduledStartDatetime(pickupTime);
        rental.setExpectedReturnDatetime(expectedReturn);
        rental.setActualPickupDatetime(pickupTime);
        rental.setOdometerOut(Integer.valueOf(odometerOut));
        rental.setConditionOut(conditionOut);
        rental.setStatus(Constants.RENTAL_ACTIVE);
        return createRental(rental);
    }

    /** Creates a rental and its advance payment in one transaction. */
    public int createRentalWithAdvancePayment(Rental rental, Payment payment)
            throws LfrlmsException {
        if (payment == null) {
            throw new ValidationException("An advance payment is required.");
        }
        return createRental(rental, payment);
    }

    /** Starts a confirmed rental after rechecking availability under a vehicle lock. */
    public void startRental(int rentalId, LocalDateTime pickupDateTime,
            int odometerOut, String conditionOut) throws LfrlmsException {
        // Validate input
        if (pickupDateTime == null || !Validator.isNonNegative(Integer.valueOf(odometerOut))
                || !Validator.isValid(conditionOut)) {
            throw new ValidationException("Enter the pickup time, odometer and condition.");
        }
        // Read the rental once to find which vehicle to lock
        Rental initial = rentalDao.findById(rentalId);
        if (initial == null) {
            throw new ValidationException("The rental record no longer exists.");
        }
        // Open a connection and start a transaction
        try (Connection connection = openConnection()) {
            beginTransaction(connection);
            try {
                // Lock the vehicle row (SELECT ... FOR UPDATE) so no other
                // booking can change it until we commit
                Vehicle vehicle = vehicleDao.lockByIdForUpdate(connection, initial.getVehicleId());
                // Read the rental again now that the vehicle is locked, in case it changed
                Rental rental = rentalDao.findById(rentalId);
                // Recheck that the rental is still Confirmed
                if (vehicle == null || rental == null
                        || !Constants.RENTAL_CONFIRMED.equals(rental.getStatus())) {
                    throw new ValidationException("Only a confirmed rental can be started.");
                }
                // Pickup must come before the expected return
                if (!pickupDateTime.isBefore(rental.getExpectedReturnDatetime())) {
                    throw new ValidationException("Pickup must be before expected return.");
                }
                // Check customer and chauffeur eligibility
                validateCustomerAndDriver(rental, rental.getExpectedReturnDatetime());
                // Recheck availability, ignoring this rental's own booking
                if (!availabilityService.isVehicleAvailable(rental.getVehicleId(),
                        pickupDateTime, rental.getExpectedReturnDatetime(),
                        Integer.valueOf(rentalId), null)) {
                    throw new VehicleNotAvailableException(
                            "The vehicle has another commitment during this rental.");
                }
                // Recheck that the chauffeur is free
                if (rental.getDriverId() != null
                        && !availabilityService.isDriverAvailable(rental.getDriverId().intValue(),
                        pickupDateTime, rental.getExpectedReturnDatetime(),
                        Integer.valueOf(rentalId))) {
                    throw new IneligibleCustomerException(
                            "The assigned chauffeur is not available for this period.");
                }
                // Save pickup details and mark it Active; a trigger marks the vehicle Rented
                rental.setActualPickupDatetime(pickupDateTime);
                rental.setOdometerOut(Integer.valueOf(odometerOut));
                rental.setConditionOut(conditionOut.trim());
                rental.setStatus(Constants.RENTAL_ACTIVE);
                if (!rentalDao.update(connection, rental)) {
                    throw new ValidationException("The rental record could not be started.");
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
            throw databaseFailure("close the rental transaction", exception);
        }
    }

    /** Checks return details, then completes the rental through the stored procedure. */
    public void completeRental(int rentalId, LocalDateTime returnDateTime,
            int odometerIn, String conditionIn) throws LfrlmsException {
        // Validate input
        if (returnDateTime == null || !Validator.isNonNegative(Integer.valueOf(odometerIn))
                || !Validator.isValid(conditionIn)) {
            throw new ValidationException("Enter the return time, odometer and condition.");
        }
        // Only an Active rental can be completed
        Rental rental = rentalDao.findById(rentalId);
        if (rental == null || !Constants.RENTAL_ACTIVE.equals(rental.getStatus())) {
            throw new ValidationException("Only an active rental can be completed.");
        }
        // Return odometer and time must not be earlier than the pickup values
        if (rental.getOdometerOut() == null || odometerIn < rental.getOdometerOut().intValue()
                || rental.getActualPickupDatetime() == null
                || !returnDateTime.isAfter(rental.getActualPickupDatetime())) {
            throw new ValidationException("Return details must follow the pickup details.");
        }
        // The procedure saves the return, sets the charge and marks it Completed;
        // a trigger then frees the vehicle
        rentalDao.completeRental(rentalId, returnDateTime, odometerIn, conditionIn.trim());
    }

    /** Cancels a confirmed rental. */
    public void cancelRental(int rentalId) throws LfrlmsException {
        Rental rental = rentalDao.findById(rentalId);
        if (rental == null || !Constants.RENTAL_CONFIRMED.equals(rental.getStatus())) {
            throw new ValidationException("Only a confirmed rental can be cancelled.");
        }
        rental.setStatus(Constants.RENTAL_CANCELLED);
        if (!rentalDao.update(rental)) {
            throw new ValidationException("The rental record could not be cancelled.");
        }
    }

    /** Finds a rental by id, or returns null. */
    public Rental findById(int rentalId) throws LfrlmsException {
        return rentalDao.findById(rentalId);
    }

    /** Searches rentals one page at a time. */
    public ArrayList<Rental> search(String text, String status, LocalDateTime from,
            LocalDateTime to, boolean outstandingOnly, String sortKey,
            boolean ascending, int pageNumber, int pageSize) throws LfrlmsException {
        return rentalDao.search(text, status, from, to, outstandingOnly,
                sortKey, ascending, pageNumber, pageSize);
    }

    /** Counts rentals that match the search filters. */
    public long countSearchResults(String text, String status, LocalDateTime from,
            LocalDateTime to, boolean outstandingOnly) throws LfrlmsException {
        return rentalDao.countSearchResults(text, status, from, to, outstandingOnly);
    }

    /** Lists a vehicle's upcoming confirmed rentals. */
    public ArrayList<Rental> upcomingForVehicle(int vehicleId, int limit)
            throws LfrlmsException {
        return rentalDao.findUpcomingForVehicle(vehicleId, limit);
    }

    private int createRental(Rental rental, Payment advancePayment) throws LfrlmsException {
        // Validate input
        validateRentalShape(rental);
        // Open a connection and start a transaction
        try (Connection connection = openConnection()) {
            beginTransaction(connection);
            try {
                // Lock the vehicle row (SELECT ... FOR UPDATE) so two bookings
                // cannot both pass the availability check
                Vehicle vehicle = vehicleDao.lockByIdForUpdate(connection, rental.getVehicleId());
                // The vehicle must exist, have a rental rate and be in service
                if (vehicle == null || vehicle.getRentalRate() == null
                        || Constants.VEHICLE_MAINTENANCE.equals(vehicle.getStatus())
                        || Constants.VEHICLE_INACTIVE.equals(vehicle.getStatus())) {
                    throw new VehicleNotAvailableException(
                            "The selected vehicle cannot be rented.");
                }
                // Check customer and chauffeur eligibility
                validateCustomerAndDriver(rental, rental.getExpectedReturnDatetime());
                // Walk-ins are checked from actual pickup, reservations from scheduled start
                LocalDateTime from = Constants.RENTAL_ACTIVE.equals(rental.getStatus())
                        ? rental.getActualPickupDatetime() : rental.getScheduledStartDatetime();
                // Recheck availability while the lock is held
                if (!availabilityService.isVehicleAvailable(rental.getVehicleId(), from,
                        rental.getExpectedReturnDatetime(), null, null)) {
                    throw new VehicleNotAvailableException(
                            "The vehicle is not available for the full requested period.");
                }
                // Check the chauffeur is free for the same period
                if (rental.getDriverId() != null
                        && !availabilityService.isDriverAvailable(rental.getDriverId().intValue(),
                        from, rental.getExpectedReturnDatetime(), null)) {
                    throw new IneligibleCustomerException(
                            "The assigned chauffeur is not available for this period.");
                }
                // Fix the current daily rate on the rental and insert it
                rental.setDailyRateApplied(vehicle.getRentalRate());
                int rentalId = rentalDao.insert(connection, rental);
                rental.setRentalId(rentalId);
                // Record any advance payment in the same transaction
                if (advancePayment != null) {
                    advancePayment.setRentalId(Integer.valueOf(rentalId));
                    advancePayment.setLeaseId(null);
                    paymentService.recordAdvanceForConfirmedRental(
                            connection, advancePayment, rental);
                }
                // Commit; the catch blocks below roll back on any failure
                commit(connection);
                return rentalId;
            } catch (LfrlmsException exception) {
                rollback(connection, exception);
                throw exception;
            } catch (RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw databaseFailure("close the rental transaction", exception);
        }
    }

    private void validateRentalShape(Rental rental) throws LfrlmsException {
        // Require customer, vehicle, a valid period and a Confirmed or Active status
        if (rental == null || rental.getCustomerId() <= 0 || rental.getVehicleId() <= 0
                || rental.getScheduledStartDatetime() == null
                || rental.getExpectedReturnDatetime() == null
                || !rental.getExpectedReturnDatetime().isAfter(rental.getScheduledStartDatetime())
                || (!Constants.RENTAL_CONFIRMED.equals(rental.getStatus())
                && !Constants.RENTAL_ACTIVE.equals(rental.getStatus()))) {
            throw new ValidationException("Enter a valid confirmed or walk-in rental.");
        }
        // Reject a start more than five minutes in the past
        if (rental.getScheduledStartDatetime().isBefore(LocalDateTime.now().minusMinutes(5))) {
            throw new ValidationException("A new rental cannot be scheduled in the past.");
        }
        // A reservation must not have pickup details yet
        if (Constants.RENTAL_CONFIRMED.equals(rental.getStatus())
                && (rental.getActualPickupDatetime() != null || rental.getOdometerOut() != null
                || rental.getConditionOut() != null)) {
            throw new ValidationException("A confirmed rental cannot contain pickup details.");
        }
        // A walk-in needs full pickup details, timed within five minutes of now
        if (Constants.RENTAL_ACTIVE.equals(rental.getStatus())
                && (rental.getActualPickupDatetime() == null || rental.getOdometerOut() == null
                || !Validator.isNonNegative(rental.getOdometerOut())
                || !Validator.isValid(rental.getConditionOut())
                || !rental.getScheduledStartDatetime().equals(
                rental.getActualPickupDatetime())
                || rental.getActualPickupDatetime().isBefore(LocalDateTime.now().minusMinutes(5))
                || rental.getActualPickupDatetime().isAfter(LocalDateTime.now().plusMinutes(5)))) {
            throw new ValidationException("A walk-in rental needs complete pickup details.");
        }
    }

    private void validateCustomerAndDriver(Rental rental, LocalDateTime contractEnd)
            throws LfrlmsException {
        // The customer must exist
        Customer customer = customerDao.findById(rental.getCustomerId());
        if (customer == null) {
            throw new IneligibleCustomerException("Select an existing customer.");
        }
        // Individuals need a licence valid to the end date; corporate rentals need a chauffeur
        if (customer instanceof IndividualCustomer) {
            IndividualCustomer individual = (IndividualCustomer) customer;
            if (!Validator.isEligibleThrough(individual.getLicenseExpiry(),
                    contractEnd.toLocalDate())) {
                throw new IneligibleCustomerException(
                        "The individual's licence must remain valid through the rental end date.");
            }
        } else if (customer instanceof CorporateCustomer && rental.getDriverId() == null) {
            throw new IneligibleCustomerException(
                    "Every corporate rental must have an active chauffeur.");
        }
        // Any assigned chauffeur must be active
        if (rental.getDriverId() != null) {
            Driver driver = driverDao.findById(rental.getDriverId().intValue());
            if (driver == null || !Constants.DRIVER_ACTIVE.equals(driver.getStatus())) {
                throw new IneligibleCustomerException("The assigned chauffeur is not active.");
            }
        }
    }
}
