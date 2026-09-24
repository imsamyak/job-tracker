package org.example.domain.constant;

/**
 * Unlike the cover letter (NONE until explicitly asked for), every job gets a fit score automatically -
 * so there's no NONE here, just where a job is in that automatic pipeline.
 */
public enum FitStatus {
    /** Queued - {@code FitScoreScheduler} picks this up on a later (deliberately not immediate) tick. */
    PENDING,
    READY,
    /** See Job.fitError. Requeued to PENDING automatically the next time the app starts, like the resume. */
    FAILED
}
