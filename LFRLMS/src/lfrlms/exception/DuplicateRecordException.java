package lfrlms.exception;

/**
 * Purpose: Reports that a unique business record already exists.
 * Concept: Checked exception inheritance.
 * Layer: Exception.
 * Rubric: Exception handling, Inheritance.
 */
public class DuplicateRecordException extends LfrlmsException {
    private static final long serialVersionUID = 1L;

    public DuplicateRecordException() {
        super();
    }

    public DuplicateRecordException(String message) {
        super(message);
    }

    public DuplicateRecordException(String message, Throwable cause) {
        super(message, cause);
    }
}
