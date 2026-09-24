package org.example.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.domain.constant.JobMode;
import org.example.domain.constant.JobType;
import org.example.domain.constant.PendingIntakeStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A job posting on its way in: the raw PDF is saved immediately (see {@code pdfPath}), and this row is
 * updated - never re-reading the PDF - once the background {@code IntakeScheduler} has read it. Only
 * turns into a real {@link Job} once it's known to be neither a duplicate nor still ambiguous; see
 * {@link PendingIntakeStatus}.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PendingIntake {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Where the raw PDF is saved on disk - outside any temp folder, so it survives an app restart. */
    @Column(nullable = false)
    private String pdfPath;

    /** The page's URL, if the caller sent one - used only to dedupe a resubmission before it's read. */
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PendingIntakeStatus status = PendingIntakeStatus.PENDING;

    // ---- populated once the scheduler has read the PDF (NEEDS_CONFIRMATION only) -------------------
    // Mirrors Job's own fields; kept here (not a serialized blob) so confirming is a plain copy, not a
    // re-parse, and so these are visible to the frontend as soon as they're known.

    private String jobId;
    private String company;
    private String role;
    @Enumerated(EnumType.STRING)
    private JobType type;
    @Enumerated(EnumType.STRING)
    private JobMode mode;
    private String location;
    // Plain nullable fields rather than the shared JobExperienceRequirement embeddable: that type's own
    // `min` column is NOT NULL (correct for a real Job, wrong here - a fresh PendingIntake has neither
    // value yet).
    private Integer experienceMin;
    private Integer experienceMax;
    @ElementCollection
    @Builder.Default
    private List<String> skills = new ArrayList<>();
    @ElementCollection
    @OrderColumn
    @Builder.Default
    private List<String> stacks = new ArrayList<>();
    @ElementCollection
    @Builder.Default
    private List<String> contacts = new ArrayList<>();
    @Lob
    private String description;

    /** The earlier job this one resembles (same company/role, within 24h) - shown for NEEDS_CONFIRMATION. */
    private UUID similarJobId;

    @Column(length = 2000)
    private String errorMessage;

    @Builder.Default
    private Instant createdAt = Instant.now();

    public void markNeedsConfirmation(String jobId, String company, String role, JobType type, JobMode mode,
                                      String location, int experienceMin, int experienceMax, List<String> skills,
                                      List<String> stacks, List<String> contacts, String description,
                                      UUID similarJobId) {
        this.jobId = jobId;
        this.company = company;
        this.role = role;
        this.type = type;
        this.mode = mode;
        this.location = location;
        this.experienceMin = experienceMin;
        this.experienceMax = experienceMax;
        this.skills = skills;
        this.stacks = stacks;
        this.contacts = contacts;
        this.description = description;
        this.similarJobId = similarJobId;
        this.status = PendingIntakeStatus.NEEDS_CONFIRMATION;
    }

    public void markFailed(String errorMessage) {
        this.errorMessage = errorMessage != null && errorMessage.length() > 2000
                ? errorMessage.substring(0, 2000) : errorMessage;
        this.status = PendingIntakeStatus.FAILED;
    }
}
