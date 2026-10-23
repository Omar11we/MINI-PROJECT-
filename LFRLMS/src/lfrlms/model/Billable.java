package lfrlms.model;

import java.math.BigDecimal;

/**
 * Purpose: Gives contracts a common way to calculate their charge.
 * Concept: Interface and polymorphism.
 * Layer: Model.
 * Rubric: interfaces/abstract classes.
 */
public interface Billable {
    BigDecimal calculateCharge();
}
