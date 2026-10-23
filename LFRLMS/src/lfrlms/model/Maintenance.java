package lfrlms.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lfrlms.util.Constants;

/**
 * Purpose: Represents vehicle service work and its completion details.
 * Concept: Encapsulation and nullable date fields.
 * Layer: Model.
 * Rubric: constructors, overloading.
 */
public class Maintenance {
    private int maintenanceId;
    private int vehicleId;
    private LocalDate serviceDate;
    private String serviceType;
    private String description;
    private BigDecimal cost;
    private LocalDate nextServiceDate;
    private LocalDate completedDate;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Maintenance() {
        status = Constants.MAINTENANCE_IN_PROGRESS;
    }

    public Maintenance(int maintenanceId, int vehicleId, LocalDate serviceDate,
            String serviceType, String description, BigDecimal cost,
            LocalDate nextServiceDate, LocalDate completedDate, String status) {
        this.maintenanceId = maintenanceId;
        this.vehicleId = vehicleId;
        this.serviceDate = serviceDate;
        this.serviceType = serviceType;
        this.description = description;
        this.cost = cost;
        this.nextServiceDate = nextServiceDate;
        this.completedDate = completedDate;
        this.status = status;
    }

    public Maintenance(Maintenance other) {
        if (other == null) {
            throw new IllegalArgumentException("Maintenance record to copy cannot be null.");
        }
        this.maintenanceId = other.maintenanceId;
        this.vehicleId = other.vehicleId;
        this.serviceDate = other.serviceDate;
        this.serviceType = other.serviceType;
        this.description = other.description;
        this.cost = other.cost;
        this.nextServiceDate = other.nextServiceDate;
        this.completedDate = other.completedDate;
        this.status = other.status;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }

    public int getMaintenanceId() {
        return maintenanceId;
    }

    public void setMaintenanceId(int maintenanceId) {
        this.maintenanceId = maintenanceId;
    }

    public int getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(int vehicleId) {
        this.vehicleId = vehicleId;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public void setServiceDate(LocalDate serviceDate) {
        this.serviceDate = serviceDate;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public LocalDate getNextServiceDate() {
        return nextServiceDate;
    }

    public void setNextServiceDate(LocalDate nextServiceDate) {
        this.nextServiceDate = nextServiceDate;
    }

    public LocalDate getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(LocalDate completedDate) {
        this.completedDate = completedDate;
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
