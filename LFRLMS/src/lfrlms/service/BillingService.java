package lfrlms.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lfrlms.exception.InvalidDateRangeException;
import lfrlms.exception.ValidationException;
import lfrlms.model.Billable;
import lfrlms.model.Rental;
import lfrlms.util.Constants;
import lfrlms.util.Validator;

// Layer: Service (business logic).
// Rubric: interfaces/abstract classes.
/** Provides Java-side billable estimates through the shared Billable interface. */
public class BillingService {
    /** Returns the charge calculated by any Billable contract. */
    public BigDecimal estimate(Billable billable) throws ValidationException {
        if (billable == null) {
            throw new ValidationException("A billable contract is required.");
        }
        BigDecimal amount = billable.calculateCharge();
        if (amount == null) {
            throw new ValidationException("The contract does not have enough details to estimate.");
        }
        return amount;
    }

    /** Estimates a rental charge from its dates and daily rate. */
    public BigDecimal estimateRental(LocalDateTime start, LocalDateTime end,
            BigDecimal dailyRate) throws ValidationException, InvalidDateRangeException {
        if (!Validator.isValid(start, end)) {
            throw new InvalidDateRangeException("The rental end must be later than its start.");
        }
        if (!Validator.isNonNegative(dailyRate)) {
            throw new ValidationException("The daily rate cannot be negative.");
        }
        Rental estimate = new Rental();
        estimate.setStatus(Constants.RENTAL_COMPLETED);
        estimate.setDailyRateApplied(dailyRate);
        estimate.setActualPickupDatetime(start);
        estimate.setActualReturnDatetime(end);
        return estimate(estimate);
    }
}
