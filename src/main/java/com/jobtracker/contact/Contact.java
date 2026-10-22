package com.jobtracker.contact;

import jakarta.persistence.*;
import java.time.Instant;

/** A person to reach about one job: usually an HR contact or recruiter found in the posting or on LinkedIn. */
@Entity
@Table(name = "contacts", indexes = @Index(columnList = "jobId"))
public class Contact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long jobId;

    @Column(nullable = false)
    private String name;

    /** Their role, for example "HR Manager" or "Technical Recruiter". */
    @Column(name = "contact_role")
    private String role;

    /** How to reach them: an email address or a phone number, as written. */
    @Column(name = "contact_info", length = 200)
    private String contact;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Contact() {}

    public Contact(Long jobId) {
        this.jobId = jobId;
    }

    public Long getId() { return id; }
    public Long getJobId() { return jobId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public Instant getCreatedAt() { return createdAt; }
}
