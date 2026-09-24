package org.example.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.example.domain.constant.CoverLetterStatus;
import org.example.domain.constant.FitStatus;
import org.example.domain.constant.JobMode;
import org.example.domain.constant.JobStatus;
import org.example.domain.constant.JobType;
import org.example.domain.embed.JobExperienceRequirement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The company's own posting id. Empty when the posting has none. */
    private String jobId;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String role;

    /**
     * [Company]_[Role]_[JobId] - the job's identity and the name of its folder.
     * Unique, so the same posting is never saved twice.
     */
    @Column(nullable = false, unique = true)
    private String folderName;

    /** The page's URL, if the caller sent one - lets a later resubmission of the same page be caught
     *  before it's even read, rather than only after paying for extraction. */
    private String url;

    @Enumerated(EnumType.STRING)
    private JobType type;

    @Enumerated(EnumType.STRING)
    private JobMode mode;

    /** City/region the posting names - often missing since not every posting states one; editable from
     *  the frontend when the extraction didn't find it. */
    private String location;

    @Embedded
    private JobExperienceRequirement experience;

    /** Skill keywords from the description, as extracted by Claude. */
    @ElementCollection
    @Builder.Default
    private List<String> skills = new ArrayList<>();

    /** Stacks for the resume (react, vue, node, java, cloud), primary first. */
    @ElementCollection
    @OrderColumn
    @Builder.Default
    private List<String> stacks = new ArrayList<>();

    @ElementCollection
    @Builder.Default
    private List<String> contacts = new ArrayList<>();

    @Lob
    @Column(name = "content", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private JobStatus status = JobStatus.NEW;

    /** Path of the generated resume PDF, set when the status becomes RESUME_READY. */
    private String resumePath;

    @Column(length = 2000)
    private String lastError;

    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Whether the person has actually applied to this job - set from the frontend, not inferred.
     * {@code @ColumnDefault} so adding this column to the existing H2 file DB (ddl-auto=update) backfills
     * existing rows with false instead of failing on a NOT NULL constraint.
     */
    @Column(nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private boolean applied = false;

    /** When {@link #applied} was last turned on; null while not applied. */
    private Instant appliedAt;

    /**
     * Unlike the resume, a cover letter is only ever generated when the person explicitly clicks
     * "Generate" - never automatically by a scheduler. {@code @ColumnDefault} so adding this column to
     * the existing H2 file DB backfills existing rows with NONE instead of a NOT NULL failure.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'NONE'")
    @Builder.Default
    private CoverLetterStatus coverLetterStatus = CoverLetterStatus.NONE;

    /** Path of the generated cover letter PDF, set once {@link #coverLetterStatus} is READY. */
    private String coverLetterPath;

    @Column(length = 2000)
    private String coverLetterError;

    /**
     * Unlike the cover letter, this IS automatic - but deliberately not immediate (see
     * {@code FitScoreScheduler}'s much coarser schedule than the resume's). {@code @ColumnDefault} so
     * adding this column backfills existing rows with PENDING (they'll get scored on the next tick, same
     * as a brand new job) instead of a NOT NULL failure.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'PENDING'")
    @Builder.Default
    private FitStatus fitStatus = FitStatus.PENDING;

    /** 0-100, set once {@link #fitStatus} is READY. */
    private Integer fitPercentage;

    /** One short sentence explaining the percentage - set alongside it, truncated defensively in
     *  {@link #markFitReady} (Claude's "one sentence" isn't a hard guarantee). */
    @Column(length = 1000)
    private String fitSummary;

    @Column(length = 2000)
    private String fitError;

    /**
     * The "referral" variant of the resume (a fixed different email, for when a referrer is asking to
     * pass the application along - see {@code Referral}), saved separately under a {@code referral/}
     * subfolder rather than replacing the normal resume. Lazy, exactly like the cover letter - never
     * automatic. {@code @ColumnDefault} for the same existing-row-backfill reason as coverLetterStatus.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'NONE'")
    @Builder.Default
    private CoverLetterStatus referralResumeStatus = CoverLetterStatus.NONE;

    private String referralResumePath;

    @Column(length = 2000)
    private String referralResumeError;

    /** Same idea as {@link #referralResumeStatus}, for the referral cover letter. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'NONE'")
    @Builder.Default
    private CoverLetterStatus referralCoverLetterStatus = CoverLetterStatus.NONE;

    private String referralCoverLetterPath;

    @Column(length = 2000)
    private String referralCoverLetterError;

    /**
     * Used only when a job the caller confirmed as genuinely new would otherwise share its
     * [Company]_[Role]_[JobId] folder name with an earlier, similar job (same company/role, no job id).
     */
    public void disambiguateFolderName(String uniqueFolderName) {
        this.folderName = uniqueFolderName;
    }

    /** Carries the URL forward from the {@code PendingIntake} it was extracted from. */
    public void attachUrl(String url) {
        this.url = url;
    }

    public void markResumeReady(String resumePath) {
        this.resumePath = resumePath;
        this.lastError = null;
        this.status = JobStatus.RESUME_READY;
    }

    /** Puts a failed job back in the queue for the scheduler. */
    public void retry() {
        this.status = JobStatus.NEW;
    }

    public void markFailed(String error) {
        this.lastError = error != null && error.length() > 2000 ? error.substring(0, 2000) : error;
        this.status = JobStatus.FAILED;
    }

    /** Toggles whether this job has been applied to; stamps or clears {@link #appliedAt} to match. */
    public void setApplied(boolean applied) {
        this.applied = applied;
        this.appliedAt = applied ? Instant.now() : null;
    }

    /** Fills in (or corrects) the location by hand, e.g. when the posting didn't state one. */
    public void setLocation(String location) {
        this.location = (location == null || location.isBlank()) ? null : location.strip();
    }

    /**
     * Corrects (or clears) the company's posting id by hand - the extractor sometimes picks up the wrong
     * one (or a stray fragment) from a posting's page. Deliberately does NOT touch {@link #folderName}:
     * that was already fixed at intake time and its folder may already hold real generated files, so
     * renaming it here would be a much bigger, riskier operation than just correcting this field.
     */
    public void setJobId(String jobId) {
        this.jobId = (jobId == null || jobId.isBlank()) ? null : jobId.strip();
    }

    /** Marks generation as running - set right before kicking off the background claude -p + PDF work. */
    public void markCoverLetterPending() {
        this.coverLetterStatus = CoverLetterStatus.PENDING;
        this.coverLetterError = null;
    }

    public void markCoverLetterReady(String coverLetterPath) {
        this.coverLetterPath = coverLetterPath;
        this.coverLetterError = null;
        this.coverLetterStatus = CoverLetterStatus.READY;
    }

    /** Clicking "Generate" again from FAILED (or even READY, to redo it) retries - no separate button. */
    public void markCoverLetterFailed(String error) {
        this.coverLetterError = error != null && error.length() > 2000 ? error.substring(0, 2000) : error;
        this.coverLetterStatus = CoverLetterStatus.FAILED;
    }

    public void markFitReady(int percentage, String summary) {
        this.fitPercentage = Math.max(0, Math.min(100, percentage));
        // Hit for real: Claude's "one short sentence" isn't a hard guarantee, and an unguarded save of an
        // over-length summary threw a SQL exception during the startup backfill - which, uncaught there,
        // took the whole app down (an exception from an ApplicationReadyEvent listener fails Boot's
        // startup). Truncating here is the actual fix; FitScoreBackfillRunner catching exceptions per-job
        // is the defense-in-depth on top of it.
        this.fitSummary = summary != null && summary.length() > 1000 ? summary.substring(0, 1000) : summary;
        this.fitError = null;
        this.fitStatus = FitStatus.READY;
    }

    public void markFitFailed(String error) {
        this.fitError = error != null && error.length() > 2000 ? error.substring(0, 2000) : error;
        this.fitStatus = FitStatus.FAILED;
    }

    /** Puts a FAILED fit score back in the queue - called automatically on app start, like the resume's {@link #retry()}. */
    public void retryFit() {
        this.fitStatus = FitStatus.PENDING;
    }

    public void markReferralResumePending() {
        this.referralResumeStatus = CoverLetterStatus.PENDING;
        this.referralResumeError = null;
    }

    public void markReferralResumeReady(String path) {
        this.referralResumePath = path;
        this.referralResumeError = null;
        this.referralResumeStatus = CoverLetterStatus.READY;
    }

    public void markReferralResumeFailed(String error) {
        this.referralResumeError = error != null && error.length() > 2000 ? error.substring(0, 2000) : error;
        this.referralResumeStatus = CoverLetterStatus.FAILED;
    }

    public void markReferralCoverLetterPending() {
        this.referralCoverLetterStatus = CoverLetterStatus.PENDING;
        this.referralCoverLetterError = null;
    }

    public void markReferralCoverLetterReady(String path) {
        this.referralCoverLetterPath = path;
        this.referralCoverLetterError = null;
        this.referralCoverLetterStatus = CoverLetterStatus.READY;
    }

    public void markReferralCoverLetterFailed(String error) {
        this.referralCoverLetterError = error != null && error.length() > 2000 ? error.substring(0, 2000) : error;
        this.referralCoverLetterStatus = CoverLetterStatus.FAILED;
    }
}
