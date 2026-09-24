package org.example.service.coverletter;

/** The letter's wording could not be produced. Never wraps a partial or guessed result. */
public class CoverLetterWriteException extends RuntimeException {

    public CoverLetterWriteException(String message) {
        super(message);
    }

    public CoverLetterWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
