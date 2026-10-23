package lfrlms.exception;

/**
 * Purpose: Reports that a record is referenced and cannot be removed.
 * Concept: Checked exception inheritance.
 * Layer: Exception.
 * Rubric: Exception handling, Inheritance.
 */
public class RecordInUseException extends LfrlmsException {
    private static final long serialVersionUID = 1L;

    public RecordInUseException() {
        super();
    }

    public RecordInUseException(String message) {
        super(message);
    }

    public RecordInUseException(String message, Throwable cause) {
        super(message, cause);
    }
}
