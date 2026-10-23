package lfrlms.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lfrlms.util.Constants;

/**
 * Purpose: Represents a fleet vehicle and its optional rental or lease rates.
 * Concept: Encapsulation, wrapper classes and copy construction.
 * Layer: Model.
 * Rubric: constructors, overloading.
 */
public class Vehicle {
    private int vehicleId;
    private String registrationNo;
    private int brandId;
    private int categoryId;
    private int branchId;
    private String model;
    private int modelYear;
    private BigDecimal rentalRate;
    private BigDecimal leaseRate;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Vehicle() {
        status = Constants.VEHICLE_AVAILABLE;
    }

    public Vehicle(int vehicleId, String registrationNo, int brandId,
            int categoryId, int branchId, String model, int modelYear,
            BigDecimal rentalRate, BigDecimal leaseRate, String status) {
        this.vehicleId = vehicleId;
        this.registrationNo = registrationNo;
        this.brandId = brandId;
        this.categoryId = categoryId;
        this.branchId = branchId;
        this.model = model;
        this.modelYear = modelYear;
        this.rentalRate = rentalRate;
        this.leaseRate = leaseRate;
        this.status = status;
    }

    public Vehicle(Vehicle other) {
        if (other == null) {
            throw new IllegalArgumentException("Vehicle to copy cannot be null.");
        }
        this.vehicleId = other.vehicleId;
        this.registrationNo = other.registrationNo;
        this.brandId = other.brandId;
        this.categoryId = other.categoryId;
        this.branchId = other.branchId;
        this.model = other.model;
        this.modelYear = other.modelYear;
        this.rentalRate = other.rentalRate;
        this.leaseRate = other.leaseRate;
        this.status = other.status;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }

    public int getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(int vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getRegistrationNo() {
        return registrationNo;
    }

    public void setRegistrationNo(String registrationNo) {
        this.registrationNo = registrationNo;
    }

    public int getBrandId() {
        return brandId;
    }

    public void setBrandId(int brandId) {
        this.brandId = brandId;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getModelYear() {
        return modelYear;
    }

    public void setModelYear(int modelYear) {
        this.modelYear = modelYear;
    }

    public BigDecimal getRentalRate() {
        return rentalRate;
    }

    public void setRentalRate(BigDecimal rentalRate) {
        this.rentalRate = rentalRate;
    }

    public BigDecimal getLeaseRate() {
        return leaseRate;
    }

    public void setLeaseRate(BigDecimal leaseRate) {
        this.leaseRate = leaseRate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
