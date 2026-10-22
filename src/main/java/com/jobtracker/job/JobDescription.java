package com.jobtracker.job;

import jakarta.persistence.*;

/** The full text of a job's description, kept apart from {@link Job} so listing jobs stays light. */
@Entity
@Table(name = "job_descriptions")
public class JobDescription {
    @Id
    private Long jobId;

    @Column(length = 60000)
    private String text;

    protected JobDescription() {}

    public JobDescription(Long jobId, String text) {
        this.jobId = jobId;
        this.text = text;
    }

    public Long getJobId() { return jobId; }
    public String getText() { return text; }
}
