package lfrlms.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import lfrlms.util.Constants;

/**
 * Purpose: Represents a reservation or rental lifecycle for one vehicle.
 * Concept: Inheritance, super constructors, polymorphism and BigDecimal.
 * Layer: Model.
 * Rubric: Inheritance, wrapper classes.
 */
public class Rental extends Contract {
    private Integer driverId;
    private LocalDateTime scheduledStartDatetime;
    private LocalDateTime expectedReturnDatetime;
    private LocalDateTime actualPickupDatetime;
    private LocalDateTime actualReturnDatetime;
    private Integer odometerOut;
    private Integer odometerIn;
    private String conditionOut;
    private String conditionIn;
    private BigDecimal dailyRateApplied;
    private BigDecimal charges;
    private String status;

    public Rental() {
        super();
        status = Constants.RENTAL_CONFIRMED;
    }

    public Rental(int rentalId, int customerId, int vehicleId, Integer driverId,
            LocalDateTime scheduledStartDatetime, LocalDateTime expectedReturnDatetime,
            LocalDateTime actualPickupDatetime, LocalDateTime actualReturnDatetime,
            Integer odometerOut, Integer odometerIn, String conditionOut,
            String conditionIn, BigDecimal dailyRateApplied, BigDecimal charges,
            String status) {
        super(rentalId, customerId, vehicleId);
        this.driverId = driverId;
        this.scheduledStartDatetime = scheduledStartDatetime;
        this.expectedReturnDatetime = expectedReturnDatetime;
        this.actualPickupDatetime = actualPickupDatetime;
        this.actualReturnDatetime = actualReturnDatetime;
        this.odometerOut = odometerOut;
        this.odometerIn = odometerIn;
        this.conditionOut = conditionOut;
        this.conditionIn = conditionIn;
        this.dailyRateApplied = dailyRateApplied;
        this.charges = charges;
        this.status = status;
    }

    public Rental(Rental other) {
        super(other);
        this.driverId = other.driverId;
        this.scheduledStartDatetime = other.scheduledStartDatetime;
        this.expectedReturnDatetime = other.expectedReturnDatetime;
        this.actualPickupDatetime = other.actualPickupDatetime;
        this.actualReturnDatetime = other.actualReturnDatetime;
        this.odometerOut = other.odometerOut;
        this.odometerIn = other.odometerIn;
        this.conditionOut = other.conditionOut;
        this.conditionIn = other.conditionIn;
        this.dailyRateApplied = other.dailyRateApplied;
        this.charges = other.charges;
        this.status = other.status;
    }

    @Override
    public String getContractType() {
        return "Rental";
    }

    @Override
    public BigDecimal calculateCharge() {
        if (!Constants.RENTAL_ACTIVE.equals(status)
                && !Constants.RENTAL_COMPLETED.equals(status)) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (dailyRateApplied == null) {
            return BigDecimal.ZERO.setScale(2);
        }

        LocalDateTime start = actualPickupDatetime == null
                ? scheduledStartDatetime : actualPickupDatetime;
        LocalDateTime end;
        if (Constants.RENTAL_COMPLETED.equals(status)) {
            end = actualReturnDatetime;
        } else {
            end = expectedReturnDatetime;
            if (end != null && end.isBefore(LocalDateTime.now())) {
                end = LocalDateTime.now();
            }
        }
        if (start == null || end == null || !end.isAfter(start)) {
            return BigDecimal.ZERO.setScale(2);
        }

        long hours = Duration.between(start, end).toHours();
        long billableDays = Math.max(1L, (hours + 23L) / 24L);
        return dailyRateApplied.multiply(BigDecimal.valueOf(billableDays))
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public int getContractId() {
        return super.getContractId();
    }

    public int getRentalId() {
        return getContractId();
    }

    public void setRentalId(int rentalId) {
        setContractId(rentalId);
    }

    public Integer getDriverId() {
        return driverId;
    }

    public void setDriverId(Integer driverId) {
        this.driverId = driverId;
    }

    public LocalDateTime getScheduledStartDatetime() {
        return scheduledStartDatetime;
    }

    public void setScheduledStartDatetime(LocalDateTime scheduledStartDatetime) {
        this.scheduledStartDatetime = scheduledStartDatetime;
    }

    public LocalDateTime getExpectedReturnDatetime() {
        return expectedReturnDatetime;
    }

    public void setExpectedReturnDatetime(LocalDateTime expectedReturnDatetime) {
        this.expectedReturnDatetime = expectedReturnDatetime;
    }

    public LocalDateTime getActualPickupDatetime() {
        return actualPickupDatetime;
    }

    public void setActualPickupDatetime(LocalDateTime actualPickupDatetime) {
        this.actualPickupDatetime = actualPickupDatetime;
    }

    public LocalDateTime getActualReturnDatetime() {
        return actualReturnDatetime;
    }

    public void setActualReturnDatetime(LocalDateTime actualReturnDatetime) {
        this.actualReturnDatetime = actualReturnDatetime;
    }

    public Integer getOdometerOut() {
        return odometerOut;
    }

    public void setOdometerOut(Integer odometerOut) {
        this.odometerOut = odometerOut;
    }

    public Integer getOdometerIn() {
        return odometerIn;
    }

    public void setOdometerIn(Integer odometerIn) {
        this.odometerIn = odometerIn;
    }

    public String getConditionOut() {
        return conditionOut;
    }

    public void setConditionOut(String conditionOut) {
        this.conditionOut = conditionOut;
    }

    public String getConditionIn() {
        return conditionIn;
    }

    public void setConditionIn(String conditionIn) {
        this.conditionIn = conditionIn;
    }

    public BigDecimal getDailyRateApplied() {
        return dailyRateApplied;
    }

    public void setDailyRateApplied(BigDecimal dailyRateApplied) {
        this.dailyRateApplied = dailyRateApplied;
    }

    public BigDecimal getCharges() {
        return charges;
    }

    public void setCharges(BigDecimal charges) {
        this.charges = charges;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
