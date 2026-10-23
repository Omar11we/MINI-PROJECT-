package lfrlms.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import lfrlms.dao.DriverDao;
import lfrlms.dao.LeaseDao;
import lfrlms.dao.MaintenanceDao;
import lfrlms.dao.RentalDao;
import lfrlms.dao.VehicleDao;
import lfrlms.exception.InvalidDateRangeException;
import lfrlms.exception.LfrlmsException;
import lfrlms.model.Driver;
import lfrlms.model.Lease;
import lfrlms.model.Maintenance;
import lfrlms.model.Rental;
import lfrlms.model.Vehicle;
import lfrlms.util.Constants;
import lfrlms.util.Validator;

// Layer: Service (business logic).
// Rubric: Collections, Inheritance.
/** Applies the shared date-overlap rule to vehicle and chauffeur assignments. */
public class AvailabilityService extends AbstractService {
    private static final int PAGE_SIZE = 100;
    private final VehicleDao vehicleDao;
    private final RentalDao rentalDao;
    private final LeaseDao leaseDao;
    private final MaintenanceDao maintenanceDao;
    private final DriverDao driverDao;

    public AvailabilityService() {
        this(new VehicleDao(), new RentalDao(), new LeaseDao(),
                new MaintenanceDao(), new DriverDao());
    }

    public AvailabilityService(VehicleDao vehicleDao, RentalDao rentalDao,
            LeaseDao leaseDao, MaintenanceDao maintenanceDao, DriverDao driverDao) {
        this.vehicleDao = vehicleDao;
        this.rentalDao = rentalDao;
        this.leaseDao = leaseDao;
        this.maintenanceDao = maintenanceDao;
        this.driverDao = driverDao;
    }

    /** Checks the date range, then lists vehicles free for that period. */
    public ArrayList<HashMap<String, Object>> searchAvailableVehicles(
            LocalDateTime from, LocalDateTime to, Integer branchId, Integer categoryId)
            throws LfrlmsException {
        validateRange(from, to);
        return vehicleDao.findAvailableVehicles(from, to, branchId, categoryId);
    }

    /** Checks a vehicle is free for a period, skipping the given contracts. */
    public boolean isVehicleAvailable(int vehicleId, LocalDateTime from,
            LocalDateTime to, Integer ignoredRentalId, Integer ignoredLeaseId)
            throws LfrlmsException {
        validateRange(from, to);
        Vehicle vehicle = vehicleDao.findById(vehicleId);
        if (vehicle == null || Constants.VEHICLE_MAINTENANCE.equals(vehicle.getStatus())
                || Constants.VEHICLE_INACTIVE.equals(vehicle.getStatus())) {
            return false;
        }
        if (hasRentalOverlap(vehicleId, from, to, ignoredRentalId)) {
            return false;
        }
        if (hasLeaseOverlap(vehicleId, from, to, ignoredLeaseId)) {
            return false;
        }
        return !hasMaintenanceOverlap(vehicleId, from, to);
    }

    /** Checks a chauffeur is active and has no overlapping rental. */
    public boolean isDriverAvailable(int driverId, LocalDateTime from,
            LocalDateTime to, Integer ignoredRentalId) throws LfrlmsException {
        validateRange(from, to);
        Driver driver = driverDao.findById(driverId);
        if (driver == null || !Constants.DRIVER_ACTIVE.equals(driver.getStatus())) {
            return false;
        }
        ArrayList<Rental> rentals = allRentals();
        for (Rental rental : rentals) {
            if (rental.getDriverId() == null || rental.getDriverId().intValue() != driverId
                    || isIgnored(rental.getRentalId(), ignoredRentalId)) {
                continue;
            }
            if (overlapsRental(rental, from, to)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasRentalOverlap(int vehicleId, LocalDateTime from,
            LocalDateTime to, Integer ignoredRentalId) throws LfrlmsException {
        ArrayList<Rental> rentals = allRentals();
        for (Rental rental : rentals) {
            if (rental.getVehicleId() == vehicleId
                    && !isIgnored(rental.getRentalId(), ignoredRentalId)
                    && overlapsRental(rental, from, to)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasLeaseOverlap(int vehicleId, LocalDateTime from,
            LocalDateTime to, Integer ignoredLeaseId) throws LfrlmsException {
        ArrayList<Lease> leases = allLeases();
        for (Lease lease : leases) {
            if (lease.getVehicleId() == vehicleId
                    && !isIgnored(lease.getLeaseId(), ignoredLeaseId)
                    && (Constants.LEASE_SCHEDULED.equals(lease.getStatus())
                    || Constants.LEASE_ACTIVE.equals(lease.getStatus()))) {
                LocalDateTime start = lease.getStartDate().atStartOfDay();
                LocalDateTime end = lease.getEndDate().plusDays(1).atStartOfDay();
                if (start.isBefore(to) && end.isAfter(from)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasMaintenanceOverlap(int vehicleId, LocalDateTime from,
            LocalDateTime to) throws LfrlmsException {
        int pageNumber = 1;
        while (true) {
            ArrayList<Maintenance> records = maintenanceDao.search(null,
                    Constants.MAINTENANCE_IN_PROGRESS, null, null,
                    "serviceDate", true, pageNumber, PAGE_SIZE);
            for (Maintenance record : records) {
                if (record.getVehicleId() == vehicleId
                        && record.getServiceDate().atStartOfDay().isBefore(to)) {
                    return true;
                }
            }
            if (records.size() < PAGE_SIZE) {
                return false;
            }
            pageNumber++;
        }
    }

    private ArrayList<Rental> allRentals() throws LfrlmsException {
        ArrayList<Rental> rentals = new ArrayList<Rental>();
        HashSet<Integer> seen = new HashSet<Integer>();
        int pageNumber = 1;
        while (true) {
            ArrayList<Rental> page = rentalDao.search(null, null, null, null, false,
                    "scheduledStart", true, pageNumber, PAGE_SIZE);
            for (Rental rental : page) {
                if (seen.add(Integer.valueOf(rental.getRentalId()))) {
                    rentals.add(rental);
                }
            }
            if (page.size() < PAGE_SIZE) {
                return rentals;
            }
            pageNumber++;
        }
    }

    private ArrayList<Lease> allLeases() throws LfrlmsException {
        ArrayList<Lease> leases = new ArrayList<Lease>();
        HashSet<Integer> seen = new HashSet<Integer>();
        int pageNumber = 1;
        while (true) {
            ArrayList<Lease> page = leaseDao.search(null, null, null, null, false,
                    "startDate", true, pageNumber, PAGE_SIZE);
            for (Lease lease : page) {
                if (seen.add(Integer.valueOf(lease.getLeaseId()))) {
                    leases.add(lease);
                }
            }
            if (page.size() < PAGE_SIZE) {
                return leases;
            }
            pageNumber++;
        }
    }

    private boolean overlapsRental(Rental rental, LocalDateTime from, LocalDateTime to) {
        LocalDateTime start;
        if (Constants.RENTAL_CONFIRMED.equals(rental.getStatus())) {
            start = rental.getScheduledStartDatetime();
            return start.isBefore(to) && rental.getExpectedReturnDatetime().isAfter(from);
        }
        if (!Constants.RENTAL_ACTIVE.equals(rental.getStatus())) {
            return false;
        }
        start = rental.getActualPickupDatetime();
        LocalDateTime expected = rental.getExpectedReturnDatetime();
        return start != null && start.isBefore(to)
                && (expected.isAfter(from) || !expected.isAfter(LocalDateTime.now()));
    }

    private boolean isIgnored(int rowId, Integer ignoredId) {
        return ignoredId != null && ignoredId.intValue() == rowId;
    }

    private void validateRange(LocalDateTime from, LocalDateTime to)
            throws InvalidDateRangeException {
        if (!Validator.isValid(from, to)) {
            throw new InvalidDateRangeException("The end of the requested period must be later.");
        }
    }
}
