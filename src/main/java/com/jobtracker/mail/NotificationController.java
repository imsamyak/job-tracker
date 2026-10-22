package com.jobtracker.mail;

import com.jobtracker.job.Job;
import com.jobtracker.job.JobRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What the mail sync did lately: jobs it added and jobs whose status it changed, with the email behind each. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    /** @param kind ADDED (the job was not tracked before) or UPDATED (its status changed) */
    public record Notification(Long id, Long jobId, String company, String role, String status, String kind, String subject,
                               String sender, String platform, String reason, Instant receivedAt, Instant createdAt, String mailId, String mailAccount) {}

    private final MailEventRepository events;
    private final JobRepository jobs;
    private final MailAccountRepository accounts;

    public NotificationController(MailEventRepository events, JobRepository jobs, MailAccountRepository accounts) {
        this.events = events;
        this.jobs = jobs;
        this.accounts = accounts;
    }

    @GetMapping
    public List<Notification> list() {
        String fallbackAccount = accounts.findAllByOrderByAddedAtAsc().stream().map(MailAccount::getEmail).findFirst().orElse(null);
        return events.findTop50ByAppliedStatusIsNotNullOrderByCreatedAtDesc().stream().map(e -> {
            Job j = e.getJobId() == null ? null : jobs.findById(e.getJobId()).orElse(null);
            boolean added = j != null && "GMAIL".equals(j.getAddedFrom()) && e.getMessageId().equals(j.getSourceMailId());
            String account = j != null && j.getSourceMailAccount() != null ? j.getSourceMailAccount() : fallbackAccount;
            return new Notification(e.getId(), e.getJobId(), j == null ? null : j.getCompany(), j == null ? null : j.getRole(),
                    e.getAppliedStatus(), added ? "ADDED" : "UPDATED", e.getSubject(), e.getSender(), j == null ? null : j.getPlatform(), e.getReason(),
                    e.getReceivedAt(), e.getCreatedAt(), e.getMessageId(), account);
        }).toList();
    }
}
