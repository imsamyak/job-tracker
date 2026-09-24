package org.example.domain.constant;

public enum CoverLetterStatus {
    /** Default - never asked for one. Unlike the resume, this is never generated automatically. */
    NONE,
    /** Generation (claude -p + PDF render) is running in the background right now. */
    PENDING,
    READY,
    /** See Job.coverLetterError. Clicking "Generate" again retries. */
    FAILED
}
