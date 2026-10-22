package com.jobtracker.intake;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Picks up waiting intakes in the background, a few at a time, so adding a job description is
 * instant and several can be added in a row without waiting for Claude.
 */
@Component
public class IntakeWorker {

    private final IntakeRepository intakes;
    private final IntakeProcessor processor;
    private final int maxParallel;
    private final ExecutorService pool;
    private final AtomicInteger running = new AtomicInteger();

    public IntakeWorker(IntakeRepository intakes, IntakeProcessor processor,
                        @Value("${jobtracker.intake.parallel:3}") int maxParallel) {
        this.intakes = intakes;
        this.processor = processor;
        this.maxParallel = maxParallel;
        this.pool = Executors.newFixedThreadPool(maxParallel);
    }

    /** An intake caught mid-read by a restart goes back in the queue instead of staying stuck. */
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        for (Intake i : intakes.findByStatusOrderByCreatedAtAsc(Intake.Status.PROCESSING)) {
            i.setStatus(Intake.Status.PENDING);
            intakes.save(i);
        }
    }

    @Scheduled(initialDelay = 2000, fixedDelayString = "${jobtracker.intake.poll-ms:1500}")
    public void tick() {
        int free = maxParallel - running.get();
        if (free <= 0) return;
        List<Intake> waiting = intakes.findByStatusOrderByCreatedAtAsc(Intake.Status.PENDING);
        for (int i = 0; i < Math.min(free, waiting.size()); i++) {
            Intake intake = waiting.get(i);
            intake.setStatus(Intake.Status.PROCESSING);
            intakes.save(intake);
            running.incrementAndGet();
            Long id = intake.getId();
            pool.submit(() -> {
                try {
                    processor.process(id);
                } finally {
                    running.decrementAndGet();
                }
            });
        }
    }
}
