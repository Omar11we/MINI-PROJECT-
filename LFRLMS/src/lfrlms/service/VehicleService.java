package lfrlms.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import lfrlms.dao.VehicleDao;
import lfrlms.exception.LfrlmsException;
import lfrlms.exception.ValidationException;
import lfrlms.model.Vehicle;
import lfrlms.util.Constants;
import lfrlms.util.Validator;

// Layer: Service (business logic).
// Rubric: Exception handling.
/** Validates vehicle data while leaving rental and lease status transitions to their owners. */
public class VehicleService {
    private final VehicleDao vehicleDao;

    public VehicleService() {
        this(new VehicleDao());
    }

    public VehicleService(VehicleDao vehicleDao) {
        this.vehicleDao = vehicleDao;
    }

    /** Validates and saves a vehicle; new ones start Available, edits keep their status. */
    public int save(Vehicle vehicle) throws LfrlmsException {
        validate(vehicle);
        if (vehicle.getVehicleId() <= 0) {
            vehicle.setStatus(Constants.VEHICLE_AVAILABLE);
            return vehicleDao.insert(vehicle);
        }
        Vehicle existing = vehicleDao.findById(vehicle.getVehicleId());
        if (existing == null) {
            throw new ValidationException("The vehicle record no longer exists.");
        }
        vehicle.setStatus(existing.getStatus());
        if (!vehicleDao.update(vehicle)) {
            throw new ValidationException("The vehicle could not be updated.");
        }
        return vehicle.getVehicleId();
    }

    /** Marks a vehicle inactive. */
    public void deactivate(int vehicleId) throws LfrlmsException {
        Vehicle vehicle = vehicleDao.findById(vehicleId);
        if (vehicle == null) {
            throw new ValidationException("The vehicle record no longer exists.");
        }
        vehicle.setStatus(Constants.VEHICLE_INACTIVE);
        vehicleDao.update(vehicle);
    }

    /** Deletes a vehicle by id. */
    public boolean delete(int vehicleId) throws LfrlmsException {
        return vehicleDao.delete(vehicleId);
    }

    /** Finds a vehicle by id, or returns null. */
    public Vehicle findById(int vehicleId) throws LfrlmsException {
        return vehicleDao.findById(vehicleId);
    }

    /** Searches vehicles one page at a time. */
    public ArrayList<Vehicle> search(String text, Integer brandId, Integer categoryId,
            Integer branchId, String status, BigDecimal minimumRentalRate,
            BigDecimal maximumRentalRate, BigDecimal minimumLeaseRate,
            BigDecimal maximumLeaseRate, LocalDateTime availableFrom,
            LocalDateTime availableTo, String sortKey, boolean ascending,
            int pageNumber, int pageSize) throws LfrlmsException {
        return vehicleDao.search(text, brandId, categoryId, branchId, status,
                minimumRentalRate, maximumRentalRate, minimumLeaseRate, maximumLeaseRate,
                availableFrom, availableTo, sortKey, ascending, pageNumber, pageSize);
    }

    /** Counts vehicles that match the search filters. */
    public long countSearchResults(String text, Integer brandId, Integer categoryId,
            Integer branchId, String status, BigDecimal minimumRentalRate,
            BigDecimal maximumRentalRate, BigDecimal minimumLeaseRate,
            BigDecimal maximumLeaseRate, LocalDateTime availableFrom,
            LocalDateTime availableTo) throws LfrlmsException {
        return vehicleDao.countSearchResults(text, brandId, categoryId, branchId, status,
                minimumRentalRate, maximumRentalRate, minimumLeaseRate, maximumLeaseRate,
                availableFrom, availableTo);
    }

    private void validate(Vehicle vehicle) throws ValidationException {
        if (vehicle == null || !Validator.isValidRegistrationNo(vehicle.getRegistrationNo())
                || vehicle.getBrandId() <= 0 || vehicle.getCategoryId() <= 0
                || vehicle.getBranchId() <= 0 || !Validator.isValid(vehicle.getModel())
                || !Validator.isInRange(Integer.valueOf(vehicle.getModelYear()), 2000, 2100)
                || (vehicle.getRentalRate() == null && vehicle.getLeaseRate() == null)
                || (vehicle.getRentalRate() != null
                && !Validator.isNonNegative(vehicle.getRentalRate()))
                || (vehicle.getLeaseRate() != null
                && !Validator.isNonNegative(vehicle.getLeaseRate()))) {
            throw new ValidationException("Enter valid vehicle details and non-negative rates.");
        }
    }
}
