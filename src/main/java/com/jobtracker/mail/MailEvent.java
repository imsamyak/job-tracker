package com.jobtracker.mail;

import jakarta.persistence.*;
import java.time.Instant;

/** An email that was looked at, what was decided about it, and whether that changed a job. Also stops it being read twice. */
@Entity
@Table(name = "mail_events")
public class MailEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 500)
    private String messageId;

    private Long jobId;
    private String outcome;
    @Column(length = 500)
    private String sender;
    @Column(length = 500)
    private String subject;
    @Column(length = 500)
    private String reason;
    private Instant receivedAt;
    /** The status the job moved to because of this email, or null when it changed nothing. */
    private String appliedStatus;
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = cut(sender); }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = cut(subject); }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = cut(reason); }
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
    public String getAppliedStatus() { return appliedStatus; }
    public void setAppliedStatus(String appliedStatus) { this.appliedStatus = appliedStatus; }
    public Instant getCreatedAt() { return createdAt; }

    private static String cut(String s) {
        return s == null ? null : (s.length() > 480 ? s.substring(0, 480) : s);
    }
}
