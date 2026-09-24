package org.example.service.intake;

/** The posting could not be turned into a {@link ExtractedJob}. Never wraps a partial or guessed result. */
public class JdExtractionException extends RuntimeException {

    public JdExtractionException(String message) {
        super(message);
    }

    public JdExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
