package com.jobtracker.mail;

import com.jobtracker.job.Job;
import com.jobtracker.job.JobRepository;
import com.jobtracker.job.JobStatus;
import com.jobtracker.secret.SecretBox;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Keeps your jobs' statuses in step with your mail. For each connected account it reads the recent inbox, picks
 * out the emails that mention a company you track, asks Claude what each one says (rejection, interview, only a
 * confirmation) and moves the matching job along. It never goes backwards: a rejected job stays rejected, and a
 * confirmation never undoes an interview.
 */
@Service
public class MailSyncService {

    private static final Logger log = LoggerFactory.getLogger(MailSyncService.class);

    private final MailAccountRepository accounts;
    private final MailEventRepository events;
    private final JobRepository jobs;
    private final MailSource source;
    private final MailClassifier classifier;
    private final SecretBox box;
    private final int windowDays;
    private final int rangeMaxMessages;
    private final int rangeMaxClassifications;
    private final int maxMessages;
    private final int maxClassifications;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Instant lastRunAt;
    private volatile RunStats lastRun;
    private volatile int readDone;
    private volatile int readTotal;

    /** What one run did: emails read, how many mentioned a tracked company, how many Claude was asked about, how many jobs changed. */
    public static final class RunStats {
        public int read;
        public int matched;
        public int asked;
        public int changed;
        public int added;
        /** The run stopped at its limit with emails still to read: the screen asks whether to read more. */
        public boolean more;
    }

    public MailSyncService(MailAccountRepository accounts, MailEventRepository events, JobRepository jobs, MailSource source,
                           MailClassifier classifier, SecretBox box,
                           @Value("${jobtracker.mail.window-days:92}") int windowDays,
                           @Value("${jobtracker.mail.range-max-messages:2000}") int rangeMaxMessages,
                           @Value("${jobtracker.mail.range-max-classifications:100}") int rangeMaxClassifications,
                           @Value("${jobtracker.mail.max-messages:300}") int maxMessages,
                           @Value("${jobtracker.mail.max-classifications:40}") int maxClassifications) {
        this.accounts = accounts;
        this.events = events;
        this.jobs = jobs;
        this.source = source;
        this.classifier = classifier;
        this.box = box;
        this.windowDays = windowDays;
        this.rangeMaxMessages = rangeMaxMessages;
        this.rangeMaxClassifications = rangeMaxClassifications;
        this.maxMessages = maxMessages;
        this.maxClassifications = maxClassifications;
    }

    public boolean isRunning() {
        return running.get();
    }

    public Instant lastRunAt() {
        return lastRunAt;
    }

    public RunStats lastRun() {
        return lastRun;
    }

    /** How far the current download is: {done, total}. Only meaningful while a sync is running. */
    public int[] progress() {
        return new int[]{readDone, readTotal};
    }

    /** The automatic run: only mail that arrived since each account was last read. */
    public boolean syncAll() {
        return syncAll(null);
    }

    /**
     * Syncs every account, one after the other. With a {@code from} date it reads all mail since then again, instead of only
     * what is new (emails already judged are skipped, so they cost nothing extra, but mail about a job added since is found).
     * Returns false at once if a sync is already going.
     */
    public boolean syncAll(Instant from) {
        return run(from, null);
    }

    /** Syncs just one account (the others are left alone), from a date or from where it was last read. */
    public boolean syncOne(long accountId, Instant from) {
        return run(from, accountId);
    }

    private boolean run(Instant from, Long onlyAccount) {
        if (!running.compareAndSet(false, true)) return false;
        try {
            purgeOld();
            RunStats stats = new RunStats();
            for (MailAccount a : accounts.findAllByOrderByAddedAtAsc()) {
                if (onlyAccount == null || onlyAccount.equals(a.getId())) syncAccount(a, stats, from);
            }
            lastRun = stats;
            lastRunAt = Instant.now();
            return true;
        } finally {
            running.set(false);
        }
    }

    /** Forgets what was decided about emails that are now older than the window: they are never looked at again. */
    void purgeOld() {
        events.deleteByReceivedAtBefore(Instant.now().minus(Duration.ofDays(windowDays)));
        rejudgeApplicationEmailsOnce();
        linkOlderJobsToTheirMail();
    }

    /** Jobs added from Gmail before the link was kept: find the email through what was recorded about it. */
    public void linkOlderJobsToTheirMail() {
        for (Job j : jobs.findAll()) {
            if (!"GMAIL".equals(j.getAddedFrom()) || (j.getSourceMailId() != null && j.getPlatform() != null)) continue;
            events.findFirstByJobIdOrderByReceivedAtAsc(j.getId()).ifPresent(e -> {
                j.setSourceMailId(e.getMessageId());
                if (j.getPlatform() == null) j.setPlatform(MailPlatform.of(e.getSender()));
                j.setSourceMailAccount(accounts.findAllByOrderByAddedAtAsc().stream().map(MailAccount::getEmail).findFirst().orElse(null));
                jobs.save(j);
            });
        }
    }

    private static final String MIGRATION_MARKER = "migration:application-lists-v1";

    /** Once: emails about applications that were judged "nothing" before lists of applications could be read are judged again. */
    private void rejudgeApplicationEmailsOnce() {
        if (events.existsByMessageId(MIGRATION_MARKER)) return;
        events.deleteByOutcomeAndSubjectContainingIgnoreCase("NONE", "appli");
        MailEvent marker = new MailEvent();
        marker.setMessageId(MIGRATION_MARKER);
        marker.setOutcome("MARKER");
        marker.setReceivedAt(Instant.now().plus(Duration.ofDays(3650))); // far ahead, so the window clean-up never removes it
        events.save(marker);
    }

    /** Package-private for tests. A failure is kept on the account and shown in Settings; the other accounts still run. */
    void syncAccount(MailAccount account) {
        syncAccount(account, new RunStats(), null);
    }

    /** Package-private for tests. */
    void syncAccount(MailAccount account, Instant from) {
        syncAccount(account, new RunStats(), from);
    }

    private void syncAccount(MailAccount account, RunStats stats, Instant from) {
        readDone = 0;
        readTotal = 0;
        currentAccount = account.getEmail();
        Instant started = Instant.now();
        int messageCap = from != null ? rangeMaxMessages : maxMessages;
        int askCap = from != null ? rangeMaxClassifications : maxClassifications;
        // only the last few months of mail count: nothing older is ever read, whatever date was asked for
        Instant earliest = started.minus(Duration.ofDays(windowDays));
        Instant since = from != null ? from
                : account.getLastSyncedAt() == null
                ? earliest
                : account.getLastSyncedAt().minus(Duration.ofDays(1)); // a day of overlap: dates in the mailbox are day-precise
        if (since.isBefore(earliest)) since = earliest;
        try {
            List<MailMessage> mail = source.fetchSince(account.getEmail(), box.unlock(account.getLockedPassword()), since, messageCap,
                    (done, total) -> {
                        readDone = done;
                        readTotal = total;
                    });
            mail.sort(Comparator.comparing(MailMessage::receivedAt));
            stats.read += mail.size();

            int asked = 0;
            boolean complete = true;
            for (MailMessage m : mail) {
                if (events.existsByMessageId(m.messageId())) continue;
                // read the jobs again each time: an earlier email in this same run may just have changed one
                List<Job> all = jobs.findAll();
                List<Job> candidates = MailMatcher.candidates(m, all);
                if (candidates.isEmpty() && !MailMatcher.looksLikeApplication(m)) continue;
                stats.matched++;
                if (asked >= askCap) {
                    complete = false; // the rest wait for the next run
                    stats.more = true;
                    break;
                }
                asked++;
                stats.asked++;
                switch (record(m, classifier.classify(m, candidates), candidates, all)) {
                    case CHANGED -> stats.changed++;
                    case ADDED -> stats.added++;
                    case NOTHING -> { }
                }
                stats.added += addedExtra;
            }

            if (complete) account.setLastSyncedAt(started);
            account.setLastError(null);
        } catch (MailVerifier.MailException | MailClassifier.ClassifyException e) {
            log.warn("Mail sync for {} stopped: {}", account.getEmail(), e.getMessage());
            account.setLastError(e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Mail sync for {} failed", account.getEmail(), e);
            account.setLastError("Something went wrong while reading this account.");
        }
        accounts.save(account);
    }

    private enum Effect { NOTHING, CHANGED, ADDED }

    private volatile String currentAccount; // the mailbox being read (one run at a time)
    private int addedExtra; // jobs added from the other applications in the email just recorded

    private Effect record(MailMessage m, MailClassifier.Verdict v, List<Job> candidates, List<Job> all) {
        Job job = candidates.stream().filter(j -> j.getId() == v.jobId()).findFirst().orElse(null); // only a job that was offered counts
        JobStatus moved = null;
        Effect effect = Effect.NOTHING;
        addedExtra = 0;
        if (v.outcome() == MailClassifier.Outcome.APPLIED && v.others() != null) {
            List<Job> known = new java.util.ArrayList<>(all);
            for (MailClassifier.Application o : v.others()) {
                Job added = addFromMail(m, MailClassifier.Outcome.APPLIED, o.company(), o.role(), o.postingId(), known);
                if (added != null) {
                    known.add(added);
                    addedExtra++;
                }
            }
        }
        if (job == null && v.outcome() != MailClassifier.Outcome.NONE) {
            job = addFromMail(m, v.outcome(), v.company(), v.role(), v.postingId(), all);
            if (job != null) {
                moved = job.getStatus();
                effect = Effect.ADDED;
            }
        } else if (job != null) {
            JobStatus next = next(job.getStatus(), v.outcome());
            if (next != null) {
                job.setStatus(next);
                jobs.save(job);
                moved = next;
                effect = Effect.CHANGED;
                log.info("Job {} ({}) is now {} because of an email: {}", job.getId(), job.getCompany(), next, m.subject());
            }
        }
        MailEvent e = new MailEvent();
        e.setMessageId(m.messageId());
        e.setJobId(job == null ? null : job.getId());
        e.setOutcome(v.outcome().name());
        e.setSender(m.from());
        e.setSubject(m.subject());
        e.setReason(v.reason());
        e.setReceivedAt(m.receivedAt());
        e.setAppliedStatus(moved == null ? null : moved.name());
        events.save(e);
        return effect;
    }

    /** How many extra jobs the last recorded email added besides its main one. */
    int extraAdded() {
        return addedExtra;
    }

    /** A job you applied to that is not tracked yet, found in an email: added with the status the email implies. Null when it is already there or unclear. */
    private Job addFromMail(MailMessage m, MailClassifier.Outcome outcome, String companyIn, String roleIn, String postingId, List<Job> all) {
        String company = companyIn == null ? "" : companyIn.strip();
        if (company.isEmpty()) return null;
        boolean roleKnown = roleIn != null && !roleIn.isBlank();
        String role = roleKnown ? roleIn.strip() : "Role not mentioned";
        String c = MailMatcher.normalize(company), r = MailMatcher.normalize(role);
        for (Job j : all) {
            String jc = MailMatcher.normalize(j.getCompany()), jr = MailMatcher.normalize(j.getRole());
            if (!jc.equals(c)) continue;
            if (!roleKnown || jr.equals(r) || jr.contains(r) || r.contains(jr)) return null; // already tracked (a missing role matches any role at the company)
        }
        Job j = new Job();
        j.setCompany(company);
        j.setRole(role);
        if (postingId != null && !postingId.isBlank()) j.setPostingId(postingId.strip());
        j.setStatus(switch (outcome) {
            case REJECTED -> JobStatus.REJECTED;
            case IN_TALKS -> JobStatus.IN_REVIEW;
            default -> JobStatus.APPLIED;
        });
        j.setAddedFrom("GMAIL");
        j.setSourceMailId(m.messageId());
        j.setPlatform(MailPlatform.of(m.from()));
        j.setSourceMailAccount(currentAccount);
        j.setAddedAt(m.receivedAt());
        j.setAddedOn(m.receivedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate());
        log.info("Added {} at {} ({}) from an email: {}", role, company, j.getStatus(), m.subject());
        return jobs.save(j);
    }

    /** The status an email moves a job to, or null when it should stay as it is. Never backwards. */
    static JobStatus next(JobStatus current, MailClassifier.Outcome outcome) {
        return switch (outcome) {
            case REJECTED -> current != JobStatus.REJECTED ? JobStatus.REJECTED : null;
            case IN_TALKS -> current == JobStatus.TO_APPLY || current == JobStatus.APPLIED ? JobStatus.IN_REVIEW : null;
            case APPLIED -> current == JobStatus.TO_APPLY ? JobStatus.APPLIED : null;
            case NONE -> null;
        };
    }
}
