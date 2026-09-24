package org.example.service.intake;

import lombok.extern.slf4j.Slf4j;
import org.example.domain.constant.JobMode;
import org.example.domain.constant.JobStatus;
import org.example.domain.constant.JobType;
import org.example.domain.constant.PendingIntakeStatus;
import org.example.domain.embed.JobExperienceRequirement;
import org.example.domain.entity.Job;
import org.example.domain.entity.PendingIntake;
import org.example.respository.JobRepository;
import org.example.respository.PendingIntakeRepository;
import org.example.service.FitScoreService;
import org.example.service.JobEventBroadcaster;
import org.example.service.JobFolderName;
import org.example.service.fit.FitScoreCalculator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Two-phase intake, so the extension never waits on Claude: {@link #accept} just saves the posting's PDF
 * and returns a {@link PendingIntake} in milliseconds - the browser's job ends there. The actual reading
 * (which takes 15-40s) happens later, off the browser entirely, when {@code IntakeScheduler} calls
 * {@link #processPending}.
 * <p>
 * Duplicate rules, applied in {@link #processPending}, in order:
 * <ol>
 *   <li><b>Job id given:</b> a job already on file for the same company and job id is a certain
 *       duplicate. A job id given but not matching anyone in that company means this is definitely a new
 *       job - none of the other rules apply.</li>
 *   <li><b>Job id not given:</b> if the single most recently saved job in the whole system has the same
 *       company and role, that's a duplicate.</li>
 *   <li><b>Job id not given, rule 2 didn't fire:</b> if some job saved in the last 24 hours has the same
 *       company and role, this is ambiguous (a real posting can be reused for a different opening) - the
 *       pending row is marked {@code NEEDS_CONFIRMATION} and resolved from the frontend, via
 *       {@link #confirmPending} or {@link #rejectPending} - never automatically, and never from the
 *       extension.</li>
 * </ol>
 */
@Slf4j
public class JobIntakeService {

    /** File name the posting's own PDF is saved under, inside the job's folder. */
    public static final String JD_FILE_NAME = "JobDescription.pdf";

    private static final Duration AMBIGUOUS_WINDOW = Duration.ofHours(24);

    private final JdExtractor extractor;
    private final JobRepository jobs;
    private final PendingIntakeRepository pendingIntakes;
    private final Set<String> knownStacks;
    private final Path pendingDir;
    private final Path generatedRoot;
    private final JobEventBroadcaster events;
    private final FitScoreService fitScores;
    /** Optional: when set, every live extraction is also run through the keyword rules and compared (shadow
     *  mode - changes nothing that intake saves). Set after construction so the constructor stays as is. */
    private org.example.service.rules.ExtractionPilotService pilot;

    public void setPilot(org.example.service.rules.ExtractionPilotService pilot) {
        this.pilot = pilot;
    }

    public JobIntakeService(JdExtractor extractor, JobRepository jobs, PendingIntakeRepository pendingIntakes,
                            Set<String> knownStacks, Path pendingDir, Path generatedRoot,
                            JobEventBroadcaster events, FitScoreCalculator fitScoreCalculator) {
        this.extractor = extractor;
        this.jobs = jobs;
        this.pendingIntakes = pendingIntakes;
        this.knownStacks = knownStacks;
        this.pendingDir = pendingDir;
        this.generatedRoot = generatedRoot;
        this.events = events;
        this.fitScores = new FitScoreService(fitScoreCalculator);
    }

    /** One posting just POSTed to {@code /api/jobs/intake}: newly queued, or a URL match found instantly. */
    public sealed interface AcceptOutcome {
        record New(PendingIntake pending) implements AcceptOutcome {
        }

        /** Same URL is already queued (or awaiting a Yes/No) - nothing new was saved. */
        record AlreadyQueued(PendingIntake pending) implements AcceptOutcome {
        }

        /** Same URL was already turned into a job - nothing was read, nothing new was saved. */
        record AlreadyTracked(Job job) implements AcceptOutcome {
        }
    }

    /** No URL to dedupe against, so this always queues a new posting - used by callers that don't have one. */
    public PendingIntake accept(byte[] jdPdfBytes) {
        return ((AcceptOutcome.New) accept(jdPdfBytes, null)).pending();
    }

    /**
     * Saves the posting and returns immediately - nothing here reads it. This is the entire job of
     * {@code POST /api/jobs/intake}, and so the entire job of the extension: it never waits on Claude.
     * <p>
     * When {@code url} is given, it's checked against jobs and pending postings already on file FIRST,
     * before anything is even saved to disk - a resubmission of the same page is caught for free, rather
     * than only after paying to read it again.
     */
    public AcceptOutcome accept(byte[] jdPdfBytes, String url) {
        if (url != null && !url.isBlank()) {
            var existingJob = jobs.findByUrl(url);
            if (existingJob.isPresent()) {
                log.info("URL already tracked as job {}; not re-reading", existingJob.get().getId());
                return new AcceptOutcome.AlreadyTracked(existingJob.get());
            }
            var existingPending = pendingIntakes.findFirstByUrlAndStatusInOrderByCreatedAtDesc(url,
                    List.of(PendingIntakeStatus.PENDING, PendingIntakeStatus.NEEDS_CONFIRMATION));
            if (existingPending.isPresent()) {
                log.info("URL already queued as pending {}; not accepting a duplicate", existingPending.get().getId());
                return new AcceptOutcome.AlreadyQueued(existingPending.get());
            }
        }

        Path pdfPath = writePdf(jdPdfBytes);
        PendingIntake saved = pendingIntakes.save(PendingIntake.builder().pdfPath(pdfPath.toString()).url(url).build());
        log.info("Accepted posting {} for background processing", saved.getId());
        events.jobsChanged();
        return new AcceptOutcome.New(saved);
    }

    /** Called by {@code IntakeScheduler} for every {@code PENDING} row; does the actual (paid) reading. */
    public void processPending(UUID pendingId) {
        PendingIntake pending = pendingIntakes.findById(pendingId).orElse(null);
        if (pending == null) {
            return; // rejected/removed while waiting its turn - nothing to do
        }
        Path pdfPath = Path.of(pending.getPdfPath());

        Job candidate;
        try {
            ExtractedJob extracted = extractor.extract(pdfPath, "");
            if (pilot != null) {
                pilot.recordShadow(pdfPath, extracted); // never throws; free (Claude's answer already exists)
            }
            candidate = toValidatedJob(extracted);
            candidate.attachUrl(pending.getUrl());
        } catch (RuntimeException e) {
            log.warn("Posting {} could not be read: {}", pendingId, e.getMessage());
            pending.markFailed(e.getMessage());
            pendingIntakes.save(pending);
            deleteQuietly(pdfPath);
            events.jobsChanged();
            return;
        }

        var duplicate = checkDuplicate(candidate);
        if (duplicate.isPresent()) {
            log.info("Posting {} is a duplicate of {}; discarding", pendingId, duplicate.get().getId());
            pendingIntakes.delete(pending);
            deleteQuietly(pdfPath);
            events.jobsChanged();
            return;
        }

        if (candidate.getJobId() == null) {
            var similar = findSimilarWithinWindow(candidate);
            if (similar.isPresent()) {
                pending.markNeedsConfirmation(candidate.getJobId(), candidate.getCompany(), candidate.getRole(),
                        candidate.getType(), candidate.getMode(), candidate.getLocation(),
                        candidate.getExperience().getMin(),
                        candidate.getExperience().getMax(), candidate.getSkills(),
                        candidate.getStacks(), candidate.getContacts(), candidate.getDescription(),
                        similar.get().getId());
                pendingIntakes.save(pending);
                log.info("Posting {} is ambiguous (similar to {}) - waiting for confirmation in the app",
                        pendingId, similar.get().getId());
                events.jobsChanged();
                return;
            }
        }

        scoreFit(candidate);
        finish(candidate, pdfPath, false);
        pendingIntakes.delete(pending);
        events.jobsChanged();
    }

    /**
     * Saves a {@code NEEDS_CONFIRMATION} row as a genuinely new job, using the fields already on it - the
     * PDF is not re-read.
     *
     * @throws JobIntakeException if the row is unknown or isn't awaiting confirmation
     */
    public Job confirmPending(UUID pendingId) {
        PendingIntake pending = pendingIntakes.findById(pendingId)
                .filter(p -> p.getStatus() == PendingIntakeStatus.NEEDS_CONFIRMATION)
                .orElseThrow(() -> new JobIntakeException("No posting is waiting for confirmation with that id"));

        Job candidate = fromPending(pending);
        Path pdfPath = Path.of(pending.getPdfPath());

        // The pending job may no longer be the latest state of the world - re-check rather than trust it.
        var duplicate = checkDuplicate(candidate);
        if (duplicate.isPresent()) {
            pendingIntakes.delete(pending);
            deleteQuietly(pdfPath);
            events.jobsChanged();
            return duplicate.get();
        }

        scoreFit(candidate);
        Job saved = finish(candidate, pdfPath, true);
        pendingIntakes.delete(pending);
        events.jobsChanged();
        return saved;
    }

    /**
     * Immediately, as part of the same background read - not a separate delayed job. {@link FitScoreService}
     * itself never throws (a failure is recorded ON the job, not propagated), so this never risks failing
     * the whole intake over a fit-score problem.
     */
    private void scoreFit(Job candidate) {
        fitScores.score(candidate);
        if (candidate.getFitError() != null) {
            log.warn("Fit score for {} - {} failed: {}", candidate.getCompany(), candidate.getRole(), candidate.getFitError());
        }
    }

    /** Discards a pending row (declining an ambiguous one, or dismissing a failed one) and its PDF. */
    public void rejectPending(UUID pendingId) {
        PendingIntake pending = pendingIntakes.findById(pendingId)
                .orElseThrow(() -> new JobIntakeException("No such pending posting"));
        deleteQuietly(Path.of(pending.getPdfPath()));
        pendingIntakes.delete(pending);
        events.jobsChanged();
    }

    private Job fromPending(PendingIntake p) {
        String folderName = JobFolderName.of(p.getCompany(), p.getRole(), p.getJobId());
        return Job.builder()
                .company(p.getCompany())
                .role(p.getRole())
                .jobId(p.getJobId())
                .folderName(folderName)
                .url(p.getUrl())
                .type(p.getType())
                .mode(p.getMode())
                .location(p.getLocation())
                .experience(new JobExperienceRequirement(p.getExperienceMin(), p.getExperienceMax()))
                .skills(p.getSkills())
                .stacks(p.getStacks())
                .contacts(p.getContacts())
                .description(p.getDescription())
                .status(JobStatus.NEW)
                .build();
    }

    /**
     * @param confirmed false: a folder-name collision here is unexpected (rules 1/2 are point-in-time
     *                  checks) and is treated as a duplicate, same as if a rule had caught it.
     *                  true: the caller already confirmed this is a genuinely different job, so a
     *                  collision here is the EXPECTED case (same company/role, no job id, so nothing
     *                  else distinguishes the folder name) - the folder name is disambiguated instead of
     *                  the save being blocked.
     */
    private Job finish(Job job, Path pdfPath, boolean confirmed) {
        var existing = jobs.findByFolderName(job.getFolderName());
        if (existing.isPresent()) {
            if (!confirmed) {
                deleteQuietly(pdfPath);
                return existing.get();
            }
            job.disambiguateFolderName(uniqueFolderName(job.getFolderName()));
        }

        Path jobFolder = generatedRoot.resolve(job.getFolderName());
        savePdf(pdfPath, jobFolder);
        Job saved = jobs.save(job);
        log.info("Saved job {} ({})", saved.getFolderName(), saved.getId());
        return saved;
    }

    private String uniqueFolderName(String base) {
        for (int n = 2; ; n++) {
            String candidate = base + "-" + n;
            if (jobs.findByFolderName(candidate).isEmpty()) {
                return candidate;
            }
        }
    }

    /** Rules 1 and 2. Rule 3 (ambiguous) is handled separately since it doesn't return a plain duplicate. */
    private Optional<Job> checkDuplicate(Job job) {
        if (job.getJobId() != null) {
            return jobs.findByCompanyAndJobId(job.getCompany(), job.getJobId());
        }
        return jobs.findTopByOrderByCreatedAtDesc()
                .filter(last -> last.getCompany().equals(job.getCompany()) && last.getRole().equals(job.getRole()));
    }

    private Optional<Job> findSimilarWithinWindow(Job job) {
        return jobs.findFirstByCompanyAndRoleAndCreatedAtAfterOrderByCreatedAtDesc(
                job.getCompany(), job.getRole(), Instant.now().minus(AMBIGUOUS_WINDOW));
    }

    private Job toValidatedJob(ExtractedJob e) {
        String company = blank(e.company()) ? null : e.company().strip();
        String role = blank(e.role()) ? null : e.role().strip();
        if (company == null) {
            throw new JobIntakeException("The posting has no recognisable company name");
        }
        if (role == null) {
            throw new JobIntakeException("The posting has no recognisable role title");
        }
        if (blank(e.description())) {
            throw new JobIntakeException("The posting has no description text");
        }

        List<String> stacks = e.stacks() == null ? List.of() : e.stacks().stream().map(String::strip).toList();
        if (stacks.isEmpty()) {
            throw new JobIntakeException("No stack could be determined for this posting");
        }
        for (String stack : stacks) {
            if (!knownStacks.contains(stack)) {
                throw new JobIntakeException("Unknown stack '" + stack + "'; known stacks are " + knownStacks);
            }
        }

        JobType type = enumValue(JobType.class, e.type(), JobType.FullTime);
        JobMode mode = enumValue(JobMode.class, e.mode(), JobMode.Onsite);
        String location = blank(e.location()) ? null : e.location().strip();

        int min = Math.max(0, e.experienceMinYears());
        int max = e.experienceMaxYears() == null ? min : Math.max(min, e.experienceMaxYears());

        String jobId = blank(e.jobId()) ? null : e.jobId().strip();
        String folderName = JobFolderName.of(company, role, jobId);

        return Job.builder()
                .company(company)
                .role(role)
                .jobId(jobId)
                .folderName(folderName)
                .type(type)
                .mode(mode)
                .location(location)
                .experience(new JobExperienceRequirement(min, max))
                .skills(e.skills() == null ? List.of() : e.skills())
                .stacks(stacks)
                .contacts(e.contacts() == null ? List.of() : e.contacts())
                .description(e.description().strip())
                .status(JobStatus.NEW)
                .build();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String raw, E fallback) {
        if (blank(raw)) {
            return fallback;
        }
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(raw.strip())) {
                return constant;
            }
        }
        throw new JobIntakeException("Unknown " + type.getSimpleName().toLowerCase(Locale.ROOT) + " '" + raw + "'");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private Path writePdf(byte[] bytes) {
        try {
            Files.createDirectories(pendingDir);
            Path file = pendingDir.resolve(UUID.randomUUID() + ".pdf");
            Files.write(file, bytes);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the incoming PDF", e);
        }
    }

    private void savePdf(Path pdfPath, Path jobFolder) {
        try {
            Files.createDirectories(jobFolder);
            Files.copy(pdfPath, jobFolder.resolve(JD_FILE_NAME), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the job description into " + jobFolder, e);
        } finally {
            deleteQuietly(pdfPath);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete {}: {}", path, e.getMessage());
        }
    }
}
