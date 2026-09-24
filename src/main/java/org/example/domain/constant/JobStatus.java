package org.example.domain.constant;

public enum JobStatus {
    /** Saved from the extension, resume not generated yet. */
    NEW,
    RESUME_READY,
    /** Resume generation failed; see Job.lastError. The next scheduler run picks it up again. */
    FAILED
}
