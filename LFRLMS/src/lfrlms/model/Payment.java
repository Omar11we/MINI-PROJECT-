package lfrlms.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lfrlms.util.Constants;

/**
 * Purpose: Represents a payment linked to exactly one rental or lease.
 * Concept: Encapsulation and wrapper classes for nullable references.
 * Layer: Model.
 * Rubric: wrapper classes.
 */
public class Payment {
    private int paymentId;
    private Integer rentalId;
    private Integer leaseId;
    private BigDecimal amount;
    private String method;
    private LocalDateTime paymentDate;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Payment() {
        status = Constants.PAYMENT_PENDING;
    }

    public Payment(int paymentId, Integer rentalId, Integer leaseId,
            BigDecimal amount, String method, LocalDateTime paymentDate, String status) {
        this.paymentId = paymentId;
        this.rentalId = rentalId;
        this.leaseId = leaseId;
        this.amount = amount;
        this.method = method;
        this.paymentDate = paymentDate;
        this.status = status;
    }

    public Payment(Payment other) {
        if (other == null) {
            throw new IllegalArgumentException("Payment to copy cannot be null.");
        }
        this.paymentId = other.paymentId;
        this.rentalId = other.rentalId;
        this.leaseId = other.leaseId;
        this.amount = other.amount;
        this.method = other.method;
        this.paymentDate = other.paymentDate;
        this.status = other.status;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }

    public int getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(int paymentId) {
        this.paymentId = paymentId;
    }

    public Integer getRentalId() {
        return rentalId;
    }

    public void setRentalId(Integer rentalId) {
        this.rentalId = rentalId;
    }

    public Integer getLeaseId() {
        return leaseId;
    }

    public void setLeaseId(Integer leaseId) {
        this.leaseId = leaseId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public LocalDateTime getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDateTime paymentDate) {
        this.paymentDate = paymentDate;
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
