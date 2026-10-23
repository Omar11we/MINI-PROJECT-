package lfrlms.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Purpose: Validates common text, identifier and date inputs before persistence.
 * Concept: Static utility methods and method overloading.
 * Layer: Utility.
 * Rubric: overloading, wrapper classes.
 */
public final class Validator {
    private static final String EMAIL_REGEX =
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    private static final String PHONE_REGEX = "^[0-9]{10}$";
    private static final String PINCODE_REGEX = "^[0-9]{6}$";
    private static final String GST_REGEX =
            "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$";
    private static final String REGISTRATION_REGEX =
            "^[A-Z]{2}[0-9]{2}[A-Z]{1,3}[0-9]{4}$";
    private static final String LICENSE_REGEX = "^[A-Z0-9/-]{5,30}$";

    private Validator() {
    }

    public static boolean isValid(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static boolean isValid(String value, String regularExpression) {
        return value != null && regularExpression != null
                && Pattern.matches(regularExpression, value.trim());
    }

    public static boolean isValid(LocalDate start, LocalDate end) {
        return start != null && end != null && end.isAfter(start);
    }

    public static boolean isValid(LocalDateTime start, LocalDateTime end) {
        return start != null && end != null && end.isAfter(start);
    }

    public static boolean isPositive(Integer value) {
        return value != null && value.intValue() > 0;
    }

    public static boolean isNonNegative(Integer value) {
        return value != null && value.intValue() >= 0;
    }

    public static boolean isInRange(Integer value, int minimum, int maximum) {
        return value != null && minimum <= maximum
                && value.intValue() >= minimum && value.intValue() <= maximum;
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isNonNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) >= 0;
    }

    public static boolean isValidEmail(String email) {
        return isValid(email, EMAIL_REGEX);
    }

    public static boolean isValidPhone(String phone) {
        return isValid(phone, PHONE_REGEX);
    }

    public static boolean isValidPincode(String pincode) {
        return isValid(pincode, PINCODE_REGEX);
    }

    public static boolean isValidGstNo(String gstNo) {
        return isValid(gstNo, GST_REGEX);
    }

    public static boolean isValidRegistrationNo(String registrationNo) {
        return isValid(registrationNo, REGISTRATION_REGEX);
    }

    public static boolean isValidLicenseNumber(String licenseNo) {
        if (!isValid(licenseNo)) {
            return false;
        }
        String normalized = licenseNo.replaceAll("\\s+", "").toUpperCase();
        return isValid(normalized, LICENSE_REGEX);
    }

    public static boolean isEligibleThrough(LocalDate licenseExpiry, LocalDate contractEnd) {
        return licenseExpiry != null && contractEnd != null
                && !licenseExpiry.isBefore(contractEnd);
    }
}
