package org.example.service;

import org.example.domain.constant.JobStatus;
import org.example.domain.constant.PendingIntakeStatus;
import org.example.domain.entity.Job;
import org.example.domain.entity.PendingIntake;
import org.example.respository.JobRepository;
import org.example.respository.PendingIntakeRepository;
import org.example.service.fit.FakeFitScoreCalculator;
import org.example.service.intake.ExtractedJob;
import org.example.service.intake.FakeJdExtractor;
import org.example.service.intake.JobIntakeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs against an in-memory database. Builds its own {@link JobIntakeService} with a
 * {@link FakeJdExtractor} rather than autowiring the real, Spring-wired one - that one talks to the real
 * Claude CLI, which this test must never do.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:intake-scheduler-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jtraker.resume.schedule-initial-delay=PT1H",
        "jtraker.resume.schedule-delay=PT1H",
        "jtraker.intake.schedule-initial-delay=PT1H",
        "jtraker.intake.schedule-delay=PT1H"
})
class IntakeSchedulerTest {

    @Autowired
    JobRepository jobs;

    @Autowired
    PendingIntakeRepository pendingIntakes;

    @Autowired
    TransactionTemplate tx;

    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path tmp;

    @BeforeEach
    void clean() {
        jobs.deleteAll();
        pendingIntakes.deleteAll();
    }

    private ExtractedJob fixture(String company, String role) {
        return new ExtractedJob(company, role, null, "FullTime", "Remote", null, 2, 5,
                List.of("java"), List.of("Java"), List.of(), "Build backend services in Java.");
    }

    private IntakeScheduler schedulerWith(JobIntakeService intake) {
        return new IntakeScheduler(pendingIntakes, intake, tx, 3);
    }

    @Test
    void readsEveryPendingPostingAndSavesTheResultingJobs() {
        var intake = new JobIntakeService(new FakeJdExtractor(fixture("Acme", "Engineer")), jobs, pendingIntakes,
                Set.of("java"), tmp.resolve("pending"), tmp.resolve("generated"), new JobEventBroadcaster(),
                new FakeFitScoreCalculator());
        intake.accept("pdf bytes".getBytes());
        var scheduler = schedulerWith(intake);

        scheduler.processPending();

        assertEquals(0, pendingIntakes.count());
        Job saved = jobs.findAll().stream().findFirst().orElseThrow();
        assertEquals(JobStatus.NEW, saved.getStatus());
        assertEquals("Acme", saved.getCompany());
    }

    @Test
    void readsManyPendingPostingsAtTheSameTime() {
        var extractionCount = new AtomicInteger();
        var intake = new JobIntakeService(new FakeJdExtractor(pdf -> {
            int n = extractionCount.incrementAndGet();
            return fixture("Company" + n, "Engineer");
        }), jobs, pendingIntakes, Set.of("java"), tmp.resolve("pending"), tmp.resolve("generated"),
                new JobEventBroadcaster(), new FakeFitScoreCalculator());
        IntStream.range(0, 8).forEach(i -> intake.accept(("pdf " + i).getBytes()));
        var scheduler = schedulerWith(intake);

        scheduler.processPending();

        assertEquals(8, extractionCount.get());
        assertEquals(8, jobs.count());
        assertEquals(0, pendingIntakes.count());
    }

    @Test
    void doesNothingWhenNothingIsPending() {
        var scheduler = schedulerWith(new JobIntakeService(new FakeJdExtractor(fixture("Acme", "Engineer")),
                jobs, pendingIntakes, Set.of("java"), tmp.resolve("pending"), tmp.resolve("generated"),
                new JobEventBroadcaster(), new FakeFitScoreCalculator()));

        scheduler.processPending(); // must not throw with an empty queue

        assertEquals(0, jobs.count());
    }

    @Test
    void aFailedPostingIsNotRetriedAutomatically() {
        var extractionCount = new AtomicInteger();
        var intake = new JobIntakeService(new FakeJdExtractor(pdf -> {
            extractionCount.incrementAndGet();
            throw new org.example.service.intake.JdExtractionException("boom");
        }), jobs, pendingIntakes, Set.of("java"), tmp.resolve("pending"), tmp.resolve("generated"),
                new JobEventBroadcaster(), new FakeFitScoreCalculator());
        intake.accept("pdf".getBytes());
        var scheduler = schedulerWith(intake);

        scheduler.processPending();
        scheduler.processPending(); // a second run must not pick the FAILED row back up

        assertEquals(1, extractionCount.get());
        assertEquals(PendingIntakeStatus.FAILED, pendingIntakes.findAll().get(0).getStatus());
    }
}
