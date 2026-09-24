package org.example.service;

/**
 * Fixed facts for the "referral" variant of the resume/cover letter - used only when explicitly asking a
 * referrer to pass the application along, never the default. Both documents are otherwise identical to
 * the normal ones (same designation logic, same tailored content) except for this email.
 */
public final class Referral {

    public static final String EMAIL = "samyakj8766@gmail.com";

    /** Subfolder name, inside the job's own generated folder, that referral documents are saved under. */
    public static final String SUBFOLDER = "referral";

    private Referral() {
    }
}
