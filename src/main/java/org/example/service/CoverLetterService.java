package org.example.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.entity.Job;
import org.example.respository.JobRepository;
import org.example.service.coverletter.CoverLetterGeneratorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Generates one job's cover letter, entirely on demand - unlike {@code ResumeScheduler}, nothing here
 * scans the database on a timer; {@link #generateAsync} is called directly by the controller the moment
 * the person clicks "Generate", and returns immediately while the actual (paid, ~10-40s) claude -p call
 * and PDF render happen on a background thread.
 */
@Slf4j
@Component
public class CoverLetterService {

    private final JobRepository jobs;
    private final CoverLetterGeneratorService generator;
    private final Path outputDir;
    private final JobEventBroadcaster events;
    private final TransactionTemplate tx;
    private final ExecutorService pool;

    public CoverLetterService(JobRepository jobs, CoverLetterGeneratorService generator,
                              @Value("${spring.generated.path}") Path outputDir, JobEventBroadcaster events,
                              TransactionTemplate tx) {
        this.jobs = jobs;
        this.generator = generator;
        this.outputDir = outputDir;
        this.events = events;
        this.tx = tx;
        // Small and separate from the resume/intake pools - this is a person clicking a button, not a
        // queue, so there's no need for more than a couple to ever run at once.
        this.pool = Executors.newFixedThreadPool(2);
    }

    /** Marks the job PENDING immediately (so the frontend can show that right away) and kicks off the rest. */
    public void generateAsync(UUID jobId) {
        Job job = jobs.findById(jobId).orElseThrow(() -> new IllegalArgumentException("No such job " + jobId));
        job.markCoverLetterPending();
        jobs.save(job);
        events.jobsChanged();
        pool.submit(() -> generate(jobId));
    }

    /**
     * Package-private so tests can call this synchronously, skipping {@link #pool} entirely.
     * <p>
     * The read-generate-save sequence runs inside one {@link TransactionTemplate} block (not
     * {@code @Transactional} - this method is called via a plain {@code this::generate}/lambda reference
     * from {@link #generateAsync}'s background thread, which bypasses Spring's proxy-based AOP entirely,
     * so a {@code @Transactional} annotation here would silently do nothing). Keeping the whole thing in
     * one transaction is required, not cosmetic: {@code job.getStacks()}/{@code getSkills()} are lazy
     * collections, and {@link CoverLetterGeneratorService#generate} reads them - outside a transaction
     * (spring.jpa.open-in-view=false closes the session as soon as {@code findById} returns) that throws
     * {@code LazyInitializationException} every time. Hit and confirmed live before this fix.
     */
    void generate(UUID id) {
        try {
            tx.executeWithoutResult(status -> {
                Job job = jobs.findById(id).orElseThrow();
                Path coverLetter = generator.generate(job, outputDir.resolve(job.getFolderName()));
                job.markCoverLetterReady(coverLetter.toString());
                jobs.save(job);
            });
            events.jobsChanged();
        } catch (Exception e) {
            log.error("Cover letter for job {} failed", id, e);
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
            tx.executeWithoutResult(status -> jobs.findById(id).ifPresent(job -> {
                job.markCoverLetterFailed(reason);
                jobs.save(job);
            }));
            events.jobsChanged();
        }
    }

    @PreDestroy
    void shutdown() {
        pool.shutdown();
    }
}
