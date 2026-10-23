package lfrlms.model;

import java.time.LocalDateTime;

/**
 * Purpose: Holds identifiers shared by rental and lease contracts.
 * Concept: Abstract class, interface implementation and inheritance.
 * Layer: Model.
 * Rubric: interfaces/abstract classes, Inheritance.
 */
public abstract class Contract implements Billable {
    private int contractId;
    private int customerId;
    private int vehicleId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected Contract() {
    }

    protected Contract(int contractId, int customerId, int vehicleId) {
        this.contractId = contractId;
        this.customerId = customerId;
        this.vehicleId = vehicleId;
    }

    protected Contract(Contract other) {
        if (other == null) {
            throw new IllegalArgumentException("Contract to copy cannot be null.");
        }
        this.contractId = other.contractId;
        this.customerId = other.customerId;
        this.vehicleId = other.vehicleId;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }

    public abstract String getContractType();

    public int getContractId() {
        return contractId;
    }

    public void setContractId(int contractId) {
        this.contractId = contractId;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public int getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(int vehicleId) {
        this.vehicleId = vehicleId;
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
