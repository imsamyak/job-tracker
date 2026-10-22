package com.jobtracker.intake;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One job description handed to the app (as text, an image or a link), on its way to becoming a job.
 * Adding is instant; reading it (which needs Claude) happens in the background.
 */
@Entity
@Table(name = "intakes")
public class Intake {

    public enum Kind { TEXT, IMAGE, LINK }

    /** DONE: a job was created. DUPLICATE: that job was already in the list. FAILED: could not be read. */
    public enum Status { PENDING, PROCESSING, DONE, DUPLICATE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    /** The pasted text, or the link. Empty for an image. */
    @Column(length = 60000)
    private String text;

    /** Where an uploaded image was saved. */
    @Column(length = 1000)
    private String imagePath;

    /** Short human description shown while it is being read, e.g. the first words or the link. */
    @Column(length = 300)
    private String label;

    /** Why it failed, or which job it duplicated. */
    @Column(length = 1000)
    private String message;

    private Long jobId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Intake() {}

    public Intake(Kind kind, String text, String imagePath, String label) {
        this.kind = kind;
        this.text = text;
        this.imagePath = imagePath;
        this.label = label;
    }

    public Long getId() { return id; }
    public Kind getKind() { return kind; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getText() { return text; }
    public String getImagePath() { return imagePath; }
    public String getLabel() { return label; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public Instant getCreatedAt() { return createdAt; }
}
