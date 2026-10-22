package com.jobtracker.mail;

import com.jobtracker.job.Job;
import com.jobtracker.job.JobRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** How the mail sync is doing, and a way to run it now instead of waiting for the next automatic run. */
@RestController
@RequestMapping("/api/mail-sync")
public class MailSyncController {

    public record Update(Long jobId, String company, String role, String status, String subject, Instant receivedAt) {}

    /** An email that mentioned one of your jobs, and what was decided about it. */
    public record Checked(String sender, String subject, String outcome, String reason, String company, Instant receivedAt) {}

    public record Run(int read, int matched, int asked, int changed, int added, boolean more) {}

    public record Progress(int done, int total) {}

    public record State(boolean running, Instant lastRunAt, Run lastRun, Progress progress, List<Update> recent, List<Checked> checked) {}

    private final MailSyncService sync;
    private final MailEventRepository events;
    private final JobRepository jobs;

    public MailSyncController(MailSyncService sync, MailEventRepository events, JobRepository jobs) {
        this.sync = sync;
        this.events = events;
        this.jobs = jobs;
    }

    @GetMapping
    public State state() {
        List<Update> recent = events.findTop10ByAppliedStatusIsNotNullOrderByReceivedAtDesc().stream().map(e -> {
            Job j = e.getJobId() == null ? null : jobs.findById(e.getJobId()).orElse(null);
            return new Update(e.getJobId(), j == null ? null : j.getCompany(), j == null ? null : j.getRole(),
                    e.getAppliedStatus(), e.getSubject(), e.getReceivedAt());
        }).toList();
        List<Checked> checked = events.findTop10ByOutcomeNotOrderByReceivedAtDesc("MARKER").stream().map(e -> {
            Job j = e.getJobId() == null ? null : jobs.findById(e.getJobId()).orElse(null);
            return new Checked(e.getSender(), e.getSubject(), e.getOutcome(), e.getReason(), j == null ? null : j.getCompany(), e.getReceivedAt());
        }).toList();
        MailSyncService.RunStats r = sync.lastRun();
        int[] p = sync.progress();
        return new State(sync.isRunning(), sync.lastRunAt(), r == null ? null : new Run(r.read, r.matched, r.asked, r.changed, r.added, r.more),
                sync.isRunning() ? new Progress(p[0], p[1]) : null, recent, checked);
    }

    /**
     * Starts a sync in the background and returns at once; the screen watches {@code GET} until it is no longer running.
     * {@code account}: only that Gmail account; without it, all of them.
     * {@code from} (a date, yyyy-mm-dd) reads all mail since then again (never further back than the last 3 months); without it, only what is new since each account was last read.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void syncNow(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) Long account) {
        Instant since = null;
        if (from != null) {
            LocalDate today = LocalDate.now();
            if (from.isAfter(today)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a start date that is not in the future.");
            }
            since = from.atStartOfDay(ZoneId.systemDefault()).toInstant();
        }
        if (sync.isRunning()) return;
        Instant start = since;
        Thread.ofVirtual().name("mail-sync-now").start(() -> {
            if (account == null) sync.syncAll(start);
            else sync.syncOne(account, start);
        });
    }
}
