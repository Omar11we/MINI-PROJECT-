package lfrlms.exception;

/**
 * Purpose: Reports that a vehicle cannot be assigned for the requested period.
 * Concept: Checked exception inheritance.
 * Layer: Exception.
 * Rubric: Exception handling, Inheritance.
 */
public class VehicleNotAvailableException extends LfrlmsException {
    private static final long serialVersionUID = 1L;

    public VehicleNotAvailableException() {
        super();
    }

    public VehicleNotAvailableException(String message) {
        super(message);
    }

    public VehicleNotAvailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
