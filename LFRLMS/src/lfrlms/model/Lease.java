package lfrlms.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lfrlms.util.Constants;

/**
 * Purpose: Represents a vehicle lease and its monthly charges.
 * Concept: Inheritance, super constructors, polymorphism and BigDecimal.
 * Layer: Model.
 * Rubric: Inheritance.
 */
public class Lease extends Contract {
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal monthlyCharge;
    private BigDecimal deposit;
    private String status;

    public Lease() {
        super();
        status = Constants.LEASE_SCHEDULED;
        deposit = BigDecimal.ZERO.setScale(2);
    }

    public Lease(int leaseId, int customerId, int vehicleId, LocalDate startDate,
            LocalDate endDate, BigDecimal monthlyCharge, BigDecimal deposit, String status) {
        super(leaseId, customerId, vehicleId);
        this.startDate = startDate;
        this.endDate = endDate;
        this.monthlyCharge = monthlyCharge;
        this.deposit = deposit;
        this.status = status;
    }

    public Lease(Lease other) {
        super(other);
        this.startDate = other.startDate;
        this.endDate = other.endDate;
        this.monthlyCharge = other.monthlyCharge;
        this.deposit = other.deposit;
        this.status = other.status;
    }

    @Override
    public String getContractType() {
        return "Lease";
    }

    @Override
    public BigDecimal calculateCharge() {
        if (!Constants.LEASE_ACTIVE.equals(status)
                && !Constants.LEASE_COMPLETED.equals(status)) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (startDate == null || endDate == null || monthlyCharge == null
                || !endDate.isAfter(startDate)) {
            return BigDecimal.ZERO.setScale(2);
        }

        long months = ChronoUnit.MONTHS.between(startDate, endDate.plusDays(1));
        long billableMonths = Math.max(1L, months);
        return monthlyCharge.multiply(BigDecimal.valueOf(billableMonths))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public int getLeaseId() {
        return getContractId();
    }

    public void setLeaseId(int leaseId) {
        setContractId(leaseId);
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getMonthlyCharge() {
        return monthlyCharge;
    }

    public void setMonthlyCharge(BigDecimal monthlyCharge) {
        this.monthlyCharge = monthlyCharge;
    }

    public BigDecimal getDeposit() {
        return deposit;
    }

    public void setDeposit(BigDecimal deposit) {
        this.deposit = deposit;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
