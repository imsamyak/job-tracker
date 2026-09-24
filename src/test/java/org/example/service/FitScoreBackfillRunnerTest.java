package org.example.service;

import org.example.domain.constant.FitStatus;
import org.example.domain.constant.JobMode;
import org.example.domain.constant.JobStatus;
import org.example.domain.constant.JobType;
import org.example.domain.embed.JobExperienceRequirement;
import org.example.domain.entity.Job;
import org.example.respository.JobRepository;
import org.example.service.fit.FakeFitScoreCalculator;
import org.example.service.fit.FitScoreResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against an in-memory database. Builds its own {@link FitScoreBackfillRunner} with a
 * {@link FakeFitScoreCalculator} rather than autowiring the real, Spring-wired one - that one talks to
 * the real Claude CLI, which this test must never do.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fit-backfill-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jtraker.resume.schedule-initial-delay=PT1H",
        "jtraker.resume.schedule-delay=PT1H"
})
class FitScoreBackfillRunnerTest {

    @Autowired
    JobRepository jobs;

    @Autowired
    TransactionTemplate tx;

    @BeforeEach
    void clean() {
        jobs.deleteAll();
    }

    private Job save(String company, FitStatus fitStatus) {
        Job job = Job.builder()
                .company(company).role("Engineer").folderName(company + "_Engineer")
                .type(JobType.FullTime).mode(JobMode.Remote)
                .experience(new JobExperienceRequirement(2, 5))
                .skills(List.of()).stacks(List.of("java")).contacts(List.of())
                .description("desc").status(JobStatus.NEW)
                .fitStatus(fitStatus)
                .build();
        return jobs.save(job);
    }

    @Test
    void scoresEveryPendingAndFailedJob() {
        Job pending = save("Acme", FitStatus.PENDING);
        Job failed = save("Beta", FitStatus.FAILED);
        Job alreadyReady = save("Gamma", FitStatus.READY);
        var runner = new FitScoreBackfillRunner(jobs, new FitScoreService(
                new FakeFitScoreCalculator(new FitScoreResult(65, "Decent overlap"))), tx);

        runner.backfill();

        assertEquals(FitStatus.READY, jobs.findById(pending.getId()).orElseThrow().getFitStatus());
        assertEquals(FitStatus.READY, jobs.findById(failed.getId()).orElseThrow().getFitStatus());
        assertEquals(65, jobs.findById(pending.getId()).orElseThrow().getFitPercentage());
        // Wasn't touched - already READY, not in the PENDING/FAILED set this runner picks up.
        assertEquals(FitStatus.READY, jobs.findById(alreadyReady.getId()).orElseThrow().getFitStatus());
    }

    @Test
    void doesNothingWhenNothingNeedsScoring() {
        save("Acme", FitStatus.READY);
        var runner = new FitScoreBackfillRunner(jobs, new FitScoreService(new FakeFitScoreCalculator()), tx);

        runner.backfill(); // must not throw
    }

    /**
     * The actual incident this guards against: an unguarded per-job failure during backfill once
     * propagated all the way up through an ApplicationReadyEvent listener and took the whole app's
     * startup down. One job failing must never stop the others from being scored.
     */
    @Test
    void oneJobsFailureDoesNotStopTheOthersOrThrow() {
        Job bad = save("Acme", FitStatus.PENDING);
        Job good = save("Beta", FitStatus.PENDING);
        var runner = new FitScoreBackfillRunner(jobs, new FitScoreService(r -> {
            if (r.company().equals("Acme")) {
                throw new org.example.service.fit.FitScoreCalculationException("boom");
            }
            return new FitScoreResult(80, "Good fit");
        }), tx);

        runner.backfill(); // must not throw despite Acme's calculator blowing up

        assertEquals(FitStatus.FAILED, jobs.findById(bad.getId()).orElseThrow().getFitStatus());
        Job reloadedGood = jobs.findById(good.getId()).orElseThrow();
        assertEquals(FitStatus.READY, reloadedGood.getFitStatus());
        assertEquals(80, reloadedGood.getFitPercentage());
    }

    @Test
    void anOverLongSummaryIsTruncatedRatherThanFailingTheSave() {
        Job job = save("Acme", FitStatus.PENDING);
        var runner = new FitScoreBackfillRunner(jobs, new FitScoreService(
                new FakeFitScoreCalculator(new FitScoreResult(50, "x".repeat(1500)))), tx);

        runner.backfill(); // must not throw

        Job reloaded = jobs.findById(job.getId()).orElseThrow();
        assertEquals(FitStatus.READY, reloaded.getFitStatus());
        assertTrue(reloaded.getFitSummary().length() <= 1000);
    }
}
