package org.example.domain.constant;

public enum PendingIntakeStatus {
    /** Just received; waiting for the scheduler to read it. */
    PENDING,
    /** Read successfully, but it looks like a job saved in the last 24h (same company+role, no job id) -
     *  the extracted fields are held on the row itself, so confirming never re-reads the PDF. */
    NEEDS_CONFIRMATION,
    /** Could not be read or was not a valid job; see PendingIntake.errorMessage. */
    FAILED
}
