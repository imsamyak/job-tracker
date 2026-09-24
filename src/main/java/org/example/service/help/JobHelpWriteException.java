package org.example.service.help;

/** An answer could not be produced. Never wraps a partial or guessed result. */
public class JobHelpWriteException extends RuntimeException {

    public JobHelpWriteException(String message) {
        super(message);
    }

    public JobHelpWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
