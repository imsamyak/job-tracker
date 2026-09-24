package org.example.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.constant.JobStatus;
import org.example.domain.entity.Job;
import org.example.respository.JobRepository;
import org.example.service.generator.ResumeGeneratorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Generates the resume of every NEW job, several at a time. A job that fails is marked FAILED with the
 * reason and is put back in the queue the next time the app starts.
 */
@Slf4j
@Component
public class ResumeScheduler {

    private final JobRepository jobs;
    private final ResumeGeneratorService generator;
    private final TransactionTemplate tx;
    private final Path outputDir;
    private final JobEventBroadcaster events;
    private final ExecutorService pool;

    public ResumeScheduler(JobRepository jobs,
                           ResumeGeneratorService generator,
                           TransactionTemplate tx,
                           JobEventBroadcaster events,
                           @Value("${spring.generated.path}") Path outputDir,
                           @Value("${jtraker.resume.parallelism:4}") int parallelism) {
        this.jobs = jobs;
        this.generator = generator;
        this.tx = tx;
        this.events = events;
        this.outputDir = outputDir;
        this.pool = Executors.newFixedThreadPool(Math.max(1, parallelism));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void requeueFailedJobs() {
        tx.executeWithoutResult(status -> jobs.findByStatusIn(List.of(JobStatus.FAILED)).forEach(Job::retry));
    }

    /** The next run starts only after this one has finished, so a job is never generated twice at once. */
    @Scheduled(initialDelayString = "${jtraker.resume.schedule-initial-delay:PT10S}",
            fixedDelayString = "${jtraker.resume.schedule-delay:PT30S}")
    public void generatePending() {
        List<UUID> ids = jobs.findByStatusIn(List.of(JobStatus.NEW)).stream().map(Job::getId).toList();
        if (ids.isEmpty()) {
            return;
        }
        log.info("Generating {} resume(s)", ids.size());
        ids.stream()
                .map(id -> CompletableFuture.runAsync(() -> generate(id), pool))
                .toList()
                .forEach(CompletableFuture::join);
    }

    private void generate(UUID id) {
        try {
            tx.executeWithoutResult(status -> {
                Job job = jobs.findById(id).orElseThrow();
                Path resume = generator.generate(job, outputDir.resolve(job.getFolderName()));
                job.markResumeReady(resume.toString());
                jobs.save(job);
            });
            events.jobsChanged();
        } catch (Exception e) {
            log.error("Resume for job {} failed", id, e);
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
            tx.executeWithoutResult(status -> jobs.findById(id).ifPresent(job -> {
                job.markFailed(reason);
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
