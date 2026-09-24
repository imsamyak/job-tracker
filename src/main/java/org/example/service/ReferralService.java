package org.example.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.entity.Job;
import org.example.respository.JobRepository;
import org.example.service.coverletter.CoverLetterGeneratorService;
import org.example.service.generator.ResumeGeneratorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Generates the "referral" resume and cover letter - same content/designation logic as the normal ones,
 * but with a different, fixed email ({@link Referral#EMAIL}) and saved under a {@code referral/}
 * subfolder. Both are lazy, exactly like the (non-referral) cover letter - only ever generated when
 * explicitly asked for, never automatically.
 */
@Slf4j
@Component
public class ReferralService {

    private final JobRepository jobs;
    private final ResumeGeneratorService resumeGenerator;
    private final CoverLetterGeneratorService coverLetterGenerator;
    private final Path outputDir;
    private final JobEventBroadcaster events;
    private final TransactionTemplate tx;
    private final ExecutorService pool;

    public ReferralService(JobRepository jobs, ResumeGeneratorService resumeGenerator,
                           CoverLetterGeneratorService coverLetterGenerator,
                           @Value("${spring.generated.path}") Path outputDir, JobEventBroadcaster events,
                           TransactionTemplate tx) {
        this.jobs = jobs;
        this.resumeGenerator = resumeGenerator;
        this.coverLetterGenerator = coverLetterGenerator;
        this.outputDir = outputDir;
        this.events = events;
        this.tx = tx;
        // Small and separate from the other pools - a person clicking a button, not a queue.
        this.pool = Executors.newFixedThreadPool(2);
    }

    public void generateResumeAsync(UUID jobId) {
        Job job = jobs.findById(jobId).orElseThrow(() -> new IllegalArgumentException("No such job " + jobId));
        job.markReferralResumePending();
        jobs.save(job);
        events.jobsChanged();
        pool.submit(() -> generateResume(jobId));
    }

    public void generateCoverLetterAsync(UUID jobId) {
        Job job = jobs.findById(jobId).orElseThrow(() -> new IllegalArgumentException("No such job " + jobId));
        job.markReferralCoverLetterPending();
        jobs.save(job);
        events.jobsChanged();
        pool.submit(() -> generateCoverLetter(jobId));
    }

    /** Package-private so tests can call these synchronously, skipping {@link #pool} entirely. */
    void generateResume(UUID id) {
        try {
            tx.executeWithoutResult(status -> {
                Job job = jobs.findById(id).orElseThrow();
                Path resume = resumeGenerator.generateReferral(job, outputDir.resolve(job.getFolderName()));
                job.markReferralResumeReady(resume.toString());
                jobs.save(job);
            });
            events.jobsChanged();
        } catch (Exception e) {
            log.error("Referral resume for job {} failed", id, e);
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
            tx.executeWithoutResult(status -> jobs.findById(id).ifPresent(job -> {
                job.markReferralResumeFailed(reason);
                jobs.save(job);
            }));
            events.jobsChanged();
        }
    }

    void generateCoverLetter(UUID id) {
        try {
            tx.executeWithoutResult(status -> {
                Job job = jobs.findById(id).orElseThrow();
                Path coverLetter = coverLetterGenerator.generateReferral(job, outputDir.resolve(job.getFolderName()));
                job.markReferralCoverLetterReady(coverLetter.toString());
                jobs.save(job);
            });
            events.jobsChanged();
        } catch (Exception e) {
            log.error("Referral cover letter for job {} failed", id, e);
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
            tx.executeWithoutResult(status -> jobs.findById(id).ifPresent(job -> {
                job.markReferralCoverLetterFailed(reason);
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
