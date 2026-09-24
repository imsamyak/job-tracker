package org.example.service.intake;

/** The posting was read but is not a valid job (missing company/role, unknown stack, ...). */
public class JobIntakeException extends RuntimeException {

    public JobIntakeException(String message) {
        super(message);
    }
}
