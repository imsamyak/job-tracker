package org.example.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.domain.entity.Job;
import org.example.respository.JobRepository;
import org.example.service.CoverLetterService;
import org.example.service.JobEventBroadcaster;
import org.example.service.JobHelpService;
import org.example.service.ReferralService;
import org.example.service.generator.SkillSelector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Read-only view of saved applications, for the frontend's job list, plus deleting one. */
@Slf4j
@RestController
public class JobController {

    private final JobRepository jobs;
    private final Path generatedRoot;
    private final JobEventBroadcaster events;
    private final CoverLetterService coverLetters;
    private final ReferralService referrals;
    private final JobHelpService help;
    private final SkillSelector skillSelector;

    /** Resume skill groups left out of the "Skills" shown for a job: they're general concepts and tooling
     *  notes, not the technical skills the person cares to see at a glance. */
    private static final java.util.Set<String> HIDDEN_SKILL_GROUPS = java.util.Set.of("Concepts", "AI-Assisted Development");

    public JobController(JobRepository jobs, @Value("${spring.generated.path}") Path generatedRoot,
                         JobEventBroadcaster events, CoverLetterService coverLetters, ReferralService referrals,
                         JobHelpService help, SkillSelector skillSelector) {
        this.jobs = jobs;
        this.generatedRoot = generatedRoot;
        this.events = events;
        this.coverLetters = coverLetters;
        this.referrals = referrals;
        this.help = help;
        this.skillSelector = skillSelector;
    }

    /**
     * The skills that actually ended up on this job's resume (the SkillSelector's final choice), flattened
     * and comma-joinable - not Claude's raw keyword list. Falls back to the raw list if selection can't run.
     */
    private List<String> resumeSkills(Job job) {
        try {
            return skillSelector.select(job.getStacks(), job.getSkills()).sections().stream()
                    .filter(section -> !HIDDEN_SKILL_GROUPS.contains(section.label()))
                    .flatMap(section -> section.skills().stream())
                    .toList();
        } catch (RuntimeException e) {
            return List.copyOf(job.getSkills());
        }
    }

    public record JobSummary(String id, String company, String role, String status,
                             String folderName, String lastError, Instant createdAt,
                             boolean applied, Instant appliedAt, String location,
                             String coverLetterStatus, String coverLetterError,
                             String fitStatus, Integer fitPercentage, String fitSummary,
                             // Live here too (not just JobDetail) so the referral-note modal's doc rows
                             // update via the normal SSE-refresh loop while a generation is in flight,
                             // same reasoning as coverLetterStatus above.
                             String referralResumeStatus, String referralResumeError,
                             String referralCoverLetterStatus, String referralCoverLetterError) {
        static JobSummary of(Job job) {
            return new JobSummary(job.getId().toString(), job.getCompany(), job.getRole(),
                    job.getStatus().name(), job.getFolderName(), job.getLastError(), job.getCreatedAt(),
                    job.isApplied(), job.getAppliedAt(), job.getLocation(),
                    job.getCoverLetterStatus().name(), job.getCoverLetterError(),
                    job.getFitStatus().name(), job.getFitPercentage(), job.getFitSummary(),
                    job.getReferralResumeStatus().name(), job.getReferralResumeError(),
                    job.getReferralCoverLetterStatus().name(), job.getReferralCoverLetterError());
        }
    }

    @GetMapping("/api/jobs")
    public List<JobSummary> list() {
        return jobs.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(JobSummary::of)
                .toList();
    }

    /** Fuller view for the row-click detail modal - everything the list view leaves out. */
    public record JobDetail(String id, String company, String role, String status, String folderName,
                            String lastError, Instant createdAt, boolean applied, Instant appliedAt,
                            String location, String coverLetterStatus, String coverLetterError,
                            String fitStatus, Integer fitPercentage, String fitSummary,
                            /** The company's own posting id - null when the posting had none. Used by the
                             *  frontend's referral-note text ("Job ID ..."), which is built entirely
                             *  client-side (no AI, no file) from this plus company/role. */
                            String jobId,
                            String referralResumeStatus, String referralResumeError,
                            String referralCoverLetterStatus, String referralCoverLetterError,
                            String url, String type, String mode, List<String> skills,
                            /** The final resume skills (no Concepts / AI tools groups) - what the UI shows. */
                            List<String> resumeSkills,
                            List<String> stacks, List<String> contacts) {
        static JobDetail of(Job job, List<String> resumeSkills) {
            // List.copyOf forces each lazy collection to load now, while the @Transactional session on
            // get() is still open - the record must not carry the live Hibernate collection itself, since
            // that throws LazyInitializationException the moment anything touches it (even .equals()) once
            // the request (and its session) has finished.
            return new JobDetail(job.getId().toString(), job.getCompany(), job.getRole(), job.getStatus().name(),
                    job.getFolderName(), job.getLastError(), job.getCreatedAt(), job.isApplied(), job.getAppliedAt(),
                    job.getLocation(), job.getCoverLetterStatus().name(), job.getCoverLetterError(),
                    job.getFitStatus().name(), job.getFitPercentage(), job.getFitSummary(), job.getJobId(),
                    job.getReferralResumeStatus().name(), job.getReferralResumeError(),
                    job.getReferralCoverLetterStatus().name(), job.getReferralCoverLetterError(),
                    job.getUrl(), job.getType() != null ? job.getType().name() : null,
                    job.getMode() != null ? job.getMode().name() : null, List.copyOf(job.getSkills()),
                    resumeSkills, List.copyOf(job.getStacks()), List.copyOf(job.getContacts()));
        }
    }

    /**
     * {@code readOnly = true} keeps the Hibernate session open long enough to read the lazy skills/stacks/
     * contacts collections while building {@link JobDetail} - open-in-view is off (spring.jpa.open-in-view
     * =false), so without this the session is already closed by the time Jackson serializes the response.
     */
    @Transactional(readOnly = true)
    @GetMapping("/api/jobs/{id}")
    public ResponseEntity<JobDetail> get(@PathVariable UUID id) {
        return jobs.findById(id).map(job -> JobDetail.of(job, resumeSkills(job))).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Marks (or unmarks) that the person has actually applied to this job - a manual note, not inferred. */
    @PutMapping("/api/jobs/{id}/applied")
    public ResponseEntity<JobSummary> setApplied(@PathVariable UUID id,
                                                 @RequestParam(value = "applied", defaultValue = "true") boolean applied) {
        Job job = jobs.findById(id).orElse(null);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        job.setApplied(applied);
        jobs.save(job);
        events.jobsChanged();
        return ResponseEntity.ok(JobSummary.of(job));
    }

    /** Fills in (or corrects/clears) the location by hand - mainly for postings the extractor found none for. */
    @PutMapping("/api/jobs/{id}/location")
    public ResponseEntity<JobSummary> setLocation(@PathVariable UUID id, @RequestParam(value = "location", required = false) String location) {
        Job job = jobs.findById(id).orElse(null);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        job.setLocation(location);
        jobs.save(job);
        events.jobsChanged();
        return ResponseEntity.ok(JobSummary.of(job));
    }

    /**
     * Corrects (or clears) the company's own posting id by hand - the extractor sometimes picks up the
     * wrong one from a posting's page. Does not rename the job's folder (see {@link Job#setJobId}).
     */
    @PutMapping("/api/jobs/{id}/job-id")
    public ResponseEntity<JobSummary> setJobId(@PathVariable UUID id, @RequestParam(value = "jobId", required = false) String jobId) {
        Job job = jobs.findById(id).orElse(null);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        job.setJobId(jobId);
        jobs.save(job);
        events.jobsChanged();
        return ResponseEntity.ok(JobSummary.of(job));
    }

    /**
     * Kicks off writing (claude -p) and rendering a tailored cover letter for this job - unlike the
     * resume, this NEVER happens automatically, only when explicitly asked for here. Returns immediately
     * (202) with the job already marked PENDING; the actual work (~10-40s) happens in the background and
     * is reflected the same way everything else is - a jobs-changed SSE event, picked up by the next
     * {@link #list()}/{@link #get} refetch.
     */
    @PostMapping("/api/jobs/{id}/cover-letter")
    public ResponseEntity<JobSummary> generateCoverLetter(@PathVariable UUID id) {
        Job job = jobs.findById(id).orElse(null);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        coverLetters.generateAsync(id);
        // Re-fetch: generateAsync already saved the PENDING status - reflect that in the response body.
        Job pending = jobs.findById(id).orElseThrow();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(JobSummary.of(pending));
    }

    /**
     * Kicks off the "referral" resume - same content, a different (fixed) email, saved under a
     * {@code referral/} subfolder. Lazy, same shape as the cover letter endpoint above. Returns
     * {@link JobSummary}, not {@link JobDetail} - the latter reads lazy collections (skills/stacks/
     * contacts) that need an open Hibernate session (see {@link #get}'s own note on this); JobSummary
     * doesn't touch them at all, so it sidesteps the problem entirely rather than needing its own
     * {@code @Transactional}. Hit for real here once already - see memory/commit history if this comes back.
     */
    @PostMapping("/api/jobs/{id}/referral/resume")
    public ResponseEntity<JobSummary> generateReferralResume(@PathVariable UUID id) {
        if (jobs.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        referrals.generateResumeAsync(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(JobSummary.of(jobs.findById(id).orElseThrow()));
    }

    /** Same as {@link #generateReferralResume}, for the referral cover letter. */
    @PostMapping("/api/jobs/{id}/referral/cover-letter")
    public ResponseEntity<JobSummary> generateReferralCoverLetter(@PathVariable UUID id) {
        if (jobs.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        referrals.generateCoverLetterAsync(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(JobSummary.of(jobs.findById(id).orElseThrow()));
    }

    /** Downloads the referral resume once {@code referralResumeStatus} is READY. */
    @GetMapping("/api/jobs/{id}/referral/resume")
    public ResponseEntity<Resource> downloadReferralResume(@PathVariable UUID id) {
        return downloadReferralFile(id, org.example.service.generator.ResumeGeneratorService.RESUME_FILE_NAME);
    }

    /** Downloads the referral cover letter once {@code referralCoverLetterStatus} is READY. */
    @GetMapping("/api/jobs/{id}/referral/cover-letter")
    public ResponseEntity<Resource> downloadReferralCoverLetter(@PathVariable UUID id) {
        return downloadReferralFile(id, org.example.service.coverletter.CoverLetterGeneratorService.COVER_LETTER_FILE_NAME);
    }

    /** The referral resume shown in the browser (a new tab) instead of downloaded. */
    @GetMapping("/api/jobs/{id}/referral/resume/view")
    public ResponseEntity<Resource> viewReferralResume(@PathVariable UUID id) {
        return asInline(downloadReferralResume(id), org.example.service.generator.ResumeGeneratorService.RESUME_FILE_NAME);
    }

    /** The referral cover letter shown in the browser (a new tab) instead of downloaded. */
    @GetMapping("/api/jobs/{id}/referral/cover-letter/view")
    public ResponseEntity<Resource> viewReferralCoverLetter(@PathVariable UUID id) {
        return asInline(downloadReferralCoverLetter(id),
                org.example.service.coverletter.CoverLetterGeneratorService.COVER_LETTER_FILE_NAME);
    }

    /** Same response, but {@code inline} so the browser opens the PDF in its viewer instead of saving it. */
    private static ResponseEntity<Resource> asInline(ResponseEntity<Resource> download, String filename) {
        if (!download.getStatusCode().is2xxSuccessful()) {
            return download;
        }
        ResponseEntity.BodyBuilder ok = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename.replace("\"", "") + "\"");
        if (download.getHeaders().getContentType() != null) {
            ok.contentType(download.getHeaders().getContentType());
        }
        return ok.body(download.getBody());
    }

    private ResponseEntity<Resource> downloadReferralFile(UUID id, String filename) {
        Job job = jobs.findById(id).orElse(null);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        Path file = generatedRoot.resolve(job.getFolderName()).resolve(org.example.service.Referral.SUBFOLDER).resolve(filename);
        if (!Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = probeContentTypeQuietly(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(new FileSystemResource(file));
    }

    /**
     * Opens the job's generated folder (resume + job description PDF) in the OS file explorer. Only makes
     * sense because this app runs on the same machine as the browser viewing it - a personal local tool,
     * not a hosted one.
     */
    @PostMapping("/api/jobs/{id}/open-folder")
    public ResponseEntity<Void> openFolder(@PathVariable UUID id) {
        Job job = jobs.findById(id).orElse(null);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        Path folder = generatedRoot.resolve(job.getFolderName());
        if (!Files.isDirectory(folder)) {
            return ResponseEntity.notFound().build();
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(folder.toFile());
            } else {
                // Headless/service context (no Desktop support) - fall back to the OS file manager directly.
                String os = System.getProperty("os.name", "").toLowerCase();
                List<String> command = os.contains("win") ? List.of("explorer.exe", folder.toString())
                        : os.contains("mac") ? List.of("open", folder.toString())
                        : List.of("xdg-open", folder.toString());
                new ProcessBuilder(command).start();
            }
        } catch (IOException e) {
            log.warn("Could not open folder {}: {}", folder, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.noContent().build();
    }

    /** The job's generated folder's absolute path, as plain text - for a "copy folder path" button next to
     *  "Open in file explorer" (useful when pasting into an application form's file picker, a terminal,
     *  Slack, etc., where opening the actual OS window doesn't help). */
    @GetMapping(path = "/api/jobs/{id}/folder-path", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> folderPath(@PathVariable UUID id) {
        Job job = jobs.findById(id).orElse(null);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(generatedRoot.resolve(job.getFolderName()).toAbsolutePath().toString());
    }

    public record HelpAnswer(String answer) {
    }

    /**
     * Answers a free-form question about this job application on the candidate's behalf - e.g. one pasted
     * straight out of the company's own application form - so the person can copy the answer in rather than
     * writing it themselves. Synchronous (unlike the cover letter/referral docs): this is an interactive
     * back-and-forth where several different questions may be asked in a row about the same job, so there's
     * no PENDING/READY status worth persisting on the job itself - the answer just comes back in the
     * response (~5-30s, same claude -p budget as everything else). {@code readOnly = true} for the same
     * reason as {@link #get} - {@link JobHelpService#answer} reads the lazy stacks/skills collections.
     */
    @Transactional(readOnly = true)
    @PostMapping("/api/jobs/{id}/help")
    public ResponseEntity<HelpAnswer> help(@PathVariable UUID id, @RequestParam String question) {
        Job job = jobs.findById(id).orElse(null);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(new HelpAnswer(help.answer(job, question)));
    }

    public record JobFile(String name, long sizeBytes) {
    }

    /** Files actually on disk in the job's generated folder - for the frontend's file/drag-drop modal. */
    @GetMapping("/api/jobs/{id}/files")
    public ResponseEntity<List<JobFile>> listFiles(@PathVariable UUID id) {
        Job job = jobs.findById(id).orElse(null);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        Path folder = generatedRoot.resolve(job.getFolderName());
        if (!Files.isDirectory(folder)) {
            return ResponseEntity.ok(List.of());
        }
        try (var stream = Files.list(folder)) {
            List<JobFile> files = stream.filter(Files::isRegularFile)
                    .map(p -> new JobFile(p.getFileName().toString(), sizeQuietly(p)))
                    .sorted(Comparator.comparing(JobFile::name))
                    .toList();
            return ResponseEntity.ok(files);
        } catch (IOException e) {
            log.warn("Could not list folder {}: {}", folder, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private static long sizeQuietly(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Streams one file from the job's generated folder - the actual bytes behind both a plain download
     * link and the frontend's "drag out of the browser" trick (a real HTTP URL is what Chrome's
     * DownloadURL drag data needs).
     */
    @GetMapping("/api/jobs/{id}/files/{filename}")
    public ResponseEntity<Resource> downloadFile(@PathVariable UUID id, @PathVariable String filename) {
        Job job = jobs.findById(id).orElse(null);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        Path folder = generatedRoot.resolve(job.getFolderName()).normalize();
        Path file = folder.resolve(filename).normalize();
        // filename comes straight off the URL path - refuse anything that would resolve outside the
        // job's own folder (e.g. "..%2F..%2Fapplication.properties"), not just a plain "../" substring.
        if (!file.startsWith(folder) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = probeContentTypeQuietly(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(new FileSystemResource(file));
    }

    /** The same file as {@link #downloadFile}, shown in the browser (a new tab) instead of saved. */
    @GetMapping("/api/jobs/{id}/view/{filename}")
    public ResponseEntity<Resource> viewFile(@PathVariable UUID id, @PathVariable String filename) {
        return asInline(downloadFile(id, filename), filename);
    }

    private static String probeContentTypeQuietly(Path file) {
        try {
            String type = Files.probeContentType(file);
            return type != null ? type : "application/octet-stream";
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    /**
     * Server-Sent Events: a plain signal (no payload) telling any open tab to refetch {@link #list()} -
     * a job was created, its status changed, or it was deleted. One-way (server to browser) is all this
     * needs, so SSE over a WebSocket: no handshake, no extra dependency, and the browser's EventSource
     * reconnects on its own if the connection drops.
     */
    @GetMapping("/api/jobs/stream")
    public SseEmitter stream() {
        return events.subscribe();
    }

    /**
     * Deletes the job record. The generated folder (resume + job description PDF) is left in place on
     * disk by default - deleting the tracker entry is not the same as deciding you no longer want those
     * files - unless {@code deleteFolder=true} is explicitly passed, e.g. from a checkbox the caller
     * ticked, not a default.
     */
    @DeleteMapping("/api/jobs/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @RequestParam(value = "deleteFolder", defaultValue = "false") boolean deleteFolder) {
        Job job = jobs.findById(id).orElse(null);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        if (deleteFolder) {
            deleteFolderQuietly(generatedRoot.resolve(job.getFolderName()));
        }
        jobs.deleteById(id);
        log.info("Deleted job {} (folder {})", id, deleteFolder ? "also deleted" : "kept");
        events.jobsChanged();
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /** Best-effort: a missing or already-cleaned-up folder must never block deleting the job record. */
    private void deleteFolderQuietly(Path folder) {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (var walk = Files.walk(folder)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    log.warn("Could not delete {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Could not walk folder {}: {}", folder, e.getMessage());
        }
    }
}
