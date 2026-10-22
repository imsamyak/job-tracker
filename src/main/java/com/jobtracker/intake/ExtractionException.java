package com.jobtracker.intake;

/** A job description could not be read. The message is written for the user to see. */
public class ExtractionException extends Exception {
    public ExtractionException(String message) {
        super(message);
    }

    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
