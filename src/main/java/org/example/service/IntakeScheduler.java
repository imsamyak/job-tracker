package org.example.service;

import lombok.extern.slf4j.Slf4j;
import org.example.domain.constant.PendingIntakeStatus;
import org.example.respository.PendingIntakeRepository;
import org.example.service.intake.JobIntakeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reads every {@code PENDING} posting, several at a time, entirely independent of whatever sent it (the
 * browser can be closed by the time this runs) - this is what makes {@code POST /api/jobs/intake} fast:
 * it only ever creates the row this picks up later.
 */
@Slf4j
@Component
public class IntakeScheduler {

    private final PendingIntakeRepository pendingIntakes;
    private final JobIntakeService intake;
    private final TransactionTemplate tx;
    private final ExecutorService pool;

    /**
     * Ids actually being read by a worker thread RIGHT NOW - a subset of every {@code PENDING} row, since
     * all of them are submitted to the pool each tick but only {@code parallelism} run at once; the rest
     * sit in the pool's internal queue still reporting {@code PENDING} in the DB. Exposed so the frontend
     * can tell "being read" apart from "waiting its turn" instead of showing every row identically.
     */
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();

    public IntakeScheduler(PendingIntakeRepository pendingIntakes, JobIntakeService intake, TransactionTemplate tx,
                           @Value("${jtraker.intake.parallelism:3}") int parallelism) {
        this.pendingIntakes = pendingIntakes;
        this.intake = intake;
        this.tx = tx;
        this.pool = Executors.newFixedThreadPool(Math.max(1, parallelism));
    }

    /** The next run starts only after this one has finished, so a posting is never read twice at once. */
    @Scheduled(initialDelayString = "${jtraker.intake.schedule-initial-delay:PT5S}",
            fixedDelayString = "${jtraker.intake.schedule-delay:PT15S}")
    public void processPending() {
        List<UUID> ids = tx.execute(status -> pendingIntakes.findByStatus(PendingIntakeStatus.PENDING)
                .stream().map(p -> p.getId()).toList());
        if (ids == null || ids.isEmpty()) {
            return;
        }
        log.info("Reading {} pending posting(s)", ids.size());
        ids.stream()
                .map(id -> CompletableFuture.runAsync(() -> {
                    active.add(id);
                    try {
                        tx.executeWithoutResult(s -> intake.processPending(id));
                    } finally {
                        active.remove(id);
                    }
                }, pool))
                .toList()
                .forEach(CompletableFuture::join);
    }

    /** Ids currently being read by a worker thread - see {@link #active}. */
    public Set<UUID> activeIds() {
        return Set.copyOf(active);
    }

    @PreDestroy
    void shutdown() {
        pool.shutdown();
    }
}
