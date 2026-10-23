package lfrlms.util;

/**
 * Purpose: Keeps shared labels and configuration keys in one place.
 * Concept: Static final constants.
 * Layer: Utility.
 * Rubric: static/final.
 */
public final class Constants {
    public static final String CUSTOMER_INDIVIDUAL = "Individual";
    public static final String CUSTOMER_CORPORATE = "Corporate";

    public static final String RENTAL_CONFIRMED = "Confirmed";
    public static final String RENTAL_ACTIVE = "Active";
    public static final String RENTAL_COMPLETED = "Completed";
    public static final String RENTAL_CANCELLED = "Cancelled";

    public static final String LEASE_SCHEDULED = "Scheduled";
    public static final String LEASE_ACTIVE = "Active";
    public static final String LEASE_COMPLETED = "Completed";
    public static final String LEASE_TERMINATED = "Terminated";

    public static final String VEHICLE_AVAILABLE = "Available";
    public static final String VEHICLE_RESERVED = "Reserved";
    public static final String VEHICLE_RENTED = "Rented";
    public static final String VEHICLE_LEASED = "Leased";
    public static final String VEHICLE_MAINTENANCE = "Maintenance";
    public static final String VEHICLE_INACTIVE = "Inactive";

    public static final String DRIVER_ACTIVE = "Active";
    public static final String DRIVER_INACTIVE = "Inactive";

    public static final String PAYMENT_CASH = "Cash";
    public static final String PAYMENT_CARD = "Card";
    public static final String PAYMENT_BANK_TRANSFER = "Bank Transfer";
    public static final String PAYMENT_UPI = "UPI";
    public static final String PAYMENT_SUCCESS = "Success";
    public static final String PAYMENT_PENDING = "Pending";
    public static final String PAYMENT_FAILED = "Failed";
    public static final String PAYMENT_REFUNDED = "Refunded";

    public static final String MAINTENANCE_IN_PROGRESS = "In Progress";
    public static final String MAINTENANCE_COMPLETE = "Complete";

    public static final String DB_URL_KEY = "db.url";
    public static final String DB_USERNAME_KEY = "db.username";
    public static final String DB_PASSWORD_KEY = "db.password";

    private Constants() {
    }
}
