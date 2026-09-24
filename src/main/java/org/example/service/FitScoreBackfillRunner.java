package org.example.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.constant.FitStatus;
import org.example.domain.entity.Job;
import org.example.respository.JobRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Catches any job that never got a fit score - either it existed before this feature, or its own scoring
 * (during intake) failed. Runs once, right when the app starts (not on a repeating schedule - a fit score
 * is meant to be computed immediately at intake; this only exists to catch what THAT missed), so it's
 * still "immediate" from the person's point of view: they see it resolve shortly after opening the app,
 * not never.
 */
@Slf4j
@Component
public class FitScoreBackfillRunner {

    private final JobRepository jobs;
    private final FitScoreService fitScores;
    private final TransactionTemplate tx;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    public FitScoreBackfillRunner(JobRepository jobs, FitScoreService fitScores, TransactionTemplate tx) {
        this.jobs = jobs;
        this.fitScores = fitScores;
        this.tx = tx;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        List<UUID> ids = jobs.findByFitStatusIn(List.of(FitStatus.PENDING, FitStatus.FAILED))
                .stream().map(Job::getId).toList();
        if (ids.isEmpty()) {
            return;
        }
        log.info("Backfilling fit score for {} job(s)", ids.size());
        ids.stream()
                .map(id -> CompletableFuture.runAsync(() -> score(id), pool))
                .toList()
                .forEach(CompletableFuture::join);
    }

    private void score(UUID id) {
        try {
            // The whole read-score-save sequence is inside one transaction so job.getStacks()/getSkills()
            // (lazy collections FitScoreService reads) stay accessible - see CoverLetterService's own
            // TransactionTemplate for why @Transactional wouldn't work here either way.
            tx.executeWithoutResult(status -> {
                Job job = jobs.findById(id).orElse(null);
                if (job == null) {
                    return;
                }
                fitScores.score(job);
                jobs.save(job);
            });
        } catch (RuntimeException e) {
            // Real incident, not hypothetical: an unguarded save once threw here (an over-length column
            // value) and, left to propagate, failed the WHOLE app's startup - an exception from an
            // ApplicationReadyEvent listener fails Spring Boot's startup outright. One job's scoring must
            // never be able to do that again, whatever the underlying cause turns out to be next time.
            log.error("Fit score backfill failed for job {}", id, e);
        }
    }

    @PreDestroy
    void shutdown() {
        pool.shutdown();
    }
}
