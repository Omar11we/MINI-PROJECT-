package lfrlms.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import lfrlms.dao.MaintenanceDao;
import lfrlms.dao.RentalDao;
import lfrlms.dao.VehicleDao;
import lfrlms.exception.LfrlmsException;
import lfrlms.exception.ValidationException;
import lfrlms.model.Maintenance;
import lfrlms.model.Rental;
import lfrlms.model.Vehicle;
import lfrlms.util.Constants;
import lfrlms.util.Validator;

// Layer: Service (business logic).
// Rubric: JDBC CRUD, Exception handling.
/** Opens and completes maintenance with matching vehicle-status transactions. */
public class MaintenanceService extends AbstractService {
    private final MaintenanceDao maintenanceDao;
    private final VehicleDao vehicleDao;
    private final RentalDao rentalDao;

    public MaintenanceService() {
        this(new MaintenanceDao(), new VehicleDao(), new RentalDao());
    }

    public MaintenanceService(MaintenanceDao maintenanceDao, VehicleDao vehicleDao,
            RentalDao rentalDao) {
        this.maintenanceDao = maintenanceDao;
        this.vehicleDao = vehicleDao;
        this.rentalDao = rentalDao;
    }

    /** Opens maintenance on a vehicle and returns its rentals booked from the service date. */
    public ArrayList<Rental> openMaintenance(Maintenance maintenance)
            throws LfrlmsException {
        validate(maintenance);
        ArrayList<Rental> futureConfirmed = findFutureConfirmedRentals(
                maintenance.getVehicleId(), maintenance.getServiceDate());
        try (Connection connection = openConnection()) {
            beginTransaction(connection);
            try {
                // Lock the vehicle row (SELECT ... FOR UPDATE) until we commit
                Vehicle vehicle = vehicleDao.lockByIdForUpdate(connection,
                        maintenance.getVehicleId());
                if (vehicle == null || Constants.VEHICLE_RENTED.equals(vehicle.getStatus())
                        || Constants.VEHICLE_LEASED.equals(vehicle.getStatus())
                        || Constants.VEHICLE_INACTIVE.equals(vehicle.getStatus())) {
                    throw new ValidationException(
                            "Maintenance cannot be opened on a rented, leased or inactive vehicle.");
                }
                vehicle.setStatus(Constants.VEHICLE_MAINTENANCE);
                int maintenanceId = maintenanceDao.insert(connection, maintenance);
                maintenance.setMaintenanceId(maintenanceId);
                vehicleDao.update(connection, vehicle);
                commit(connection);
                return futureConfirmed;
            } catch (LfrlmsException exception) {
                rollback(connection, exception);
                throw exception;
            } catch (RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw databaseFailure("close the maintenance transaction", exception);
        }
    }

    /** Completes in-progress maintenance and makes the vehicle Available again. */
    public void completeMaintenance(int maintenanceId, LocalDate completedDate)
            throws LfrlmsException {
        if (completedDate == null) {
            throw new ValidationException("Enter the maintenance completion date.");
        }
        Maintenance initial = maintenanceDao.findById(maintenanceId);
        if (initial == null || !Constants.MAINTENANCE_IN_PROGRESS.equals(initial.getStatus())) {
            throw new ValidationException("Only in-progress maintenance can be completed.");
        }
        try (Connection connection = openConnection()) {
            beginTransaction(connection);
            try {
                // Lock the vehicle row (SELECT ... FOR UPDATE) until we commit
                Vehicle vehicle = vehicleDao.lockByIdForUpdate(connection, initial.getVehicleId());
                Maintenance maintenance = maintenanceDao.findById(maintenanceId);
                if (maintenance == null
                        || !Constants.MAINTENANCE_IN_PROGRESS.equals(maintenance.getStatus())) {
                    throw new ValidationException("The maintenance record is no longer active.");
                }
                maintenance.setCompletedDate(completedDate);
                maintenance.setStatus(Constants.MAINTENANCE_COMPLETE);
                maintenanceDao.update(connection, maintenance);
                if (vehicle != null) {
                    vehicle.setStatus(Constants.VEHICLE_AVAILABLE);
                    vehicleDao.update(connection, vehicle);
                }
                commit(connection);
            } catch (LfrlmsException exception) {
                rollback(connection, exception);
                throw exception;
            } catch (RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw databaseFailure("close the maintenance transaction", exception);
        }
    }

    /** Searches maintenance records one page at a time. */
    public ArrayList<Maintenance> search(String text, String status,
            LocalDate serviceFrom, LocalDate serviceTo, String sortKey,
            boolean ascending, int pageNumber, int pageSize) throws LfrlmsException {
        return maintenanceDao.search(text, status, serviceFrom, serviceTo,
                sortKey, ascending, pageNumber, pageSize);
    }

    /** Counts maintenance records that match the search filters. */
    public long countSearchResults(String text, String status, LocalDate serviceFrom,
            LocalDate serviceTo) throws LfrlmsException {
        return maintenanceDao.countSearchResults(text, status, serviceFrom, serviceTo);
    }

    private ArrayList<Rental> findFutureConfirmedRentals(int vehicleId, LocalDate serviceDate)
            throws LfrlmsException {
        ArrayList<Rental> warnings = new ArrayList<Rental>();
        int pageNumber = 1;
        while (true) {
            ArrayList<Rental> rentals = rentalDao.search(null, Constants.RENTAL_CONFIRMED,
                    serviceDate.atStartOfDay(), null, false, "scheduledStart", true,
                    pageNumber, 100);
            for (Rental rental : rentals) {
                if (rental.getVehicleId() == vehicleId
                        && !rental.getScheduledStartDatetime().isBefore(
                        serviceDate.atStartOfDay())) {
                    warnings.add(rental);
                }
            }
            if (rentals.size() < 100) {
                return warnings;
            }
            pageNumber++;
        }
    }

    private void validate(Maintenance maintenance) throws ValidationException {
        if (maintenance == null || maintenance.getVehicleId() <= 0
                || maintenance.getServiceDate() == null
                || !Validator.isValid(maintenance.getServiceType())
                || !Validator.isNonNegative(maintenance.getCost())
                || !Constants.MAINTENANCE_IN_PROGRESS.equals(maintenance.getStatus())) {
            throw new ValidationException("Enter valid in-progress maintenance details.");
        }
    }
}
