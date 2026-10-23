package lfrlms.exception;

/**
 * Purpose: Reports a date range whose end is not later than its start.
 * Concept: Checked exception inheritance.
 * Layer: Exception.
 * Rubric: Exception handling, Inheritance.
 */
public class InvalidDateRangeException extends LfrlmsException {
    private static final long serialVersionUID = 1L;

    public InvalidDateRangeException() {
        super();
    }

    public InvalidDateRangeException(String message) {
        super(message);
    }

    public InvalidDateRangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
