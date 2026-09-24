package org.example.service.fit;

/** The fit score could not be calculated. Never wraps a partial or guessed result. */
public class FitScoreCalculationException extends RuntimeException {

    public FitScoreCalculationException(String message) {
        super(message);
    }

    public FitScoreCalculationException(String message, Throwable cause) {
        super(message, cause);
    }
}
