package com.jobtracker.resume;

import jakarta.persistence.*;
import java.time.Instant;

/** The one resume the app keeps: your sample resume, used as the template and to fill in your profile. */
@Entity
@Table(name = "resumes")
public class Resume {
    /** There is only ever one, so its id never changes. */
    static final long THE_ONE = 1L;

    @Id
    private Long id = THE_ONE;

    private String filename;
    private long size;
    private Instant uploadedAt = Instant.now();

    public Long getId() { return id; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
}
