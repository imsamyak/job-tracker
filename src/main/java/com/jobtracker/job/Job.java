package com.jobtracker.job;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One job in the list. The database is the source of truth: everything that adds jobs (a pasted JD, a
 * screenshot, a link, later mail or an extension) ends up as a row here. The id is ours; the
 * posting's own reference number is {@code postingId}.
 */
@Entity
@Table(name = "jobs")
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String role;

    private String location;

    /** Remote, Hybrid or Onsite when the posting says. */
    private String workMode;

    /** FullTime, PartTime, Contract or Internship when the posting says. */
    private String jobType;

    private String experience;
    private String salary;

    /** The employer's or board's own reference for the posting (requisition number). */
    private String postingId;

    /** Where the posting was found: LinkedIn, Naukri, Workday, a company site... */
    private String platform;

    /** The posting's address, when the job was added from a link. */
    @Column(length = 2000)
    private String link;

    /** How it was added: TEXT, IMAGE or LINK (later also MAIL, MANUAL...). */
    private String addedFrom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.TO_APPLY;

    @Column(nullable = false)
    private LocalDate addedOn = LocalDate.now();

    /** When it was added, with the time of day. Jobs added before this existed have none. */
    private Instant addedAt = Instant.now();

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> skills = new ArrayList<>();

    /** For a job added from Gmail: the email's Message-ID and the account it is in, so the email can be opened. */
    @Column(length = 500)
    private String sourceMailId;
    private String sourceMailAccount;

    public String getSourceMailId() { return sourceMailId; }
    public void setSourceMailId(String sourceMailId) { this.sourceMailId = sourceMailId; }
    public String getSourceMailAccount() { return sourceMailAccount; }
    public void setSourceMailAccount(String sourceMailAccount) { this.sourceMailAccount = sourceMailAccount; }

    public Long getId() { return id; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getWorkMode() { return workMode; }
    public void setWorkMode(String workMode) { this.workMode = workMode; }
    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }
    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }
    public String getSalary() { return salary; }
    public void setSalary(String salary) { this.salary = salary; }
    public String getPostingId() { return postingId; }
    public void setPostingId(String postingId) { this.postingId = postingId; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public String getAddedFrom() { return addedFrom; }
    public void setAddedFrom(String addedFrom) { this.addedFrom = addedFrom; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public Instant getAddedAt() { return addedAt; }
    public void setAddedAt(Instant addedAt) { this.addedAt = addedAt; }
    public LocalDate getAddedOn() { return addedOn; }
    public void setAddedOn(LocalDate addedOn) { this.addedOn = addedOn; }
    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }
}
