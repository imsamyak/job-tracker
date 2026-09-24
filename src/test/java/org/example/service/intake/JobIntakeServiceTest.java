package org.example.service.intake;

import org.example.domain.constant.JobMode;
import org.example.domain.constant.JobStatus;
import org.example.domain.constant.JobType;
import org.example.domain.constant.PendingIntakeStatus;
import org.example.domain.entity.Job;
import org.example.domain.entity.PendingIntake;
import org.example.respository.JobRepository;
import org.example.respository.PendingIntakeRepository;
import org.example.service.JobEventBroadcaster;
import org.example.service.fit.FakeFitScoreCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Runs against an in-memory database; the real ./data file and ./generated folder are never touched. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:intake-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jtraker.resume.schedule-initial-delay=PT1H",
        "jtraker.resume.schedule-delay=PT1H",
        "jtraker.intake.schedule-initial-delay=PT1H",
        "jtraker.intake.schedule-delay=PT1H"
})
class JobIntakeServiceTest {

    @Autowired
    JobRepository jobs;

    @Autowired
    PendingIntakeRepository pendingIntakes;

    // Not static: each test gets its own pending/generated folders, so nothing leaks between tests.
    @org.junit.jupiter.api.io.TempDir
    Path tmp;

    private static ExtractedJob fixture(String company, String role, String jobId, List<String> stacks) {
        return new ExtractedJob(company, role, jobId, "FullTime", "Remote", null, 2, 5,
                stacks, List.of("Java", "Kafka"), List.of(), "Build backend services in Java with Kafka.");
    }

    private JobIntakeService serviceWith(JdExtractor extractor) {
        return serviceWith(extractor, new FakeFitScoreCalculator());
    }

    private JobIntakeService serviceWith(JdExtractor extractor, org.example.service.fit.FitScoreCalculator fitScoreCalculator) {
        return new JobIntakeService(extractor, jobs, pendingIntakes, Set.of("react", "vue", "node", "java", "cloud"),
                tmp.resolve("pending"), tmp.resolve("generated"), new JobEventBroadcaster(), fitScoreCalculator);
    }

    /** Phase 1 (accept) then phase 2 (process) back to back, as IntakeScheduler would do moments later. */
    private UUID acceptAndProcess(JobIntakeService service, byte[] pdfBytes) {
        PendingIntake accepted = service.accept(pdfBytes);
        assertEquals(PendingIntakeStatus.PENDING, accepted.getStatus());
        service.processPending(accepted.getId());
        return accepted.getId();
    }

    @BeforeEach
    void clean() {
        jobs.deleteAll();
        pendingIntakes.deleteAll();
    }

    // ---- accept() itself: fast, never reads the posting ----------------------------------------

    @Test
    void acceptNeverCallsTheExtractor() {
        var callCount = new AtomicInteger();
        var service = serviceWith(new FakeJdExtractor(pdf -> {
            callCount.incrementAndGet();
            return fixture("Acme", "Engineer", null, List.of("java"));
        }));

        PendingIntake accepted = service.accept("some pdf bytes".getBytes());

        assertEquals(PendingIntakeStatus.PENDING, accepted.getStatus());
        assertEquals(0, callCount.get(), "accept() must return before anything reads the posting");
        assertEquals(1, pendingIntakes.count());
        assertEquals(0, jobs.count());
    }

    // ---- processPending(): the actual (fake) reading + duplicate rules -------------------------

    @Test
    @org.springframework.transaction.annotation.Transactional // keeps the session open so job.getStacks() below can lazy-load
    void processingASuccessfulPostingSavesTheJobAndTheJdPdfAndRemovesThePendingRow() throws Exception {
        var service = serviceWith(new FakeJdExtractor(fixture("Acme Corp", "Backend Engineer", "42", List.of("java"))));

        UUID id = acceptAndProcess(service, "%PDF-1.4 fake".getBytes());

        assertTrue(pendingIntakes.findById(id).isEmpty(), "resolved pending rows are removed");
        Job job = jobs.findAll().stream().findFirst().orElseThrow();
        assertEquals("Acme-Corp_Backend-Engineer_42", job.getFolderName());
        assertEquals(JobStatus.NEW, job.getStatus());
        assertEquals(JobType.FullTime, job.getType());
        assertEquals(JobMode.Remote, job.getMode());
        assertEquals(List.of("java"), job.getStacks());

        Path jdFile = tmp.resolve("generated").resolve("Acme-Corp_Backend-Engineer_42").resolve(JobIntakeService.JD_FILE_NAME);
        assertTrue(Files.isRegularFile(jdFile));
        assertArrayEquals("%PDF-1.4 fake".getBytes(), Files.readAllBytes(jdFile));
    }

    @Test
    void aSuccessfulPostingGetsItsFitScoreImmediately() {
        var service = serviceWith(new FakeJdExtractor(fixture("Acme", "Engineer", null, List.of("java"))),
                new org.example.service.fit.FakeFitScoreCalculator(
                        new org.example.service.fit.FitScoreResult(72, "Solid backend overlap")));

        acceptAndProcess(service, "x".getBytes());

        Job job = jobs.findAll().stream().findFirst().orElseThrow();
        assertEquals(org.example.domain.constant.FitStatus.READY, job.getFitStatus());
        assertEquals(72, job.getFitPercentage());
        assertEquals("Solid backend overlap", job.getFitSummary());
    }

    @Test
    void aFitScoreFailureDoesNotFailTheWholeIntake() {
        var service = serviceWith(new FakeJdExtractor(fixture("Acme", "Engineer", null, List.of("java"))),
                org.example.service.fit.FakeFitScoreCalculator.failing("claude refused"));

        acceptAndProcess(service, "x".getBytes());

        Job job = jobs.findAll().stream().findFirst().orElseThrow();
        assertEquals(JobStatus.NEW, job.getStatus(), "the job itself must still save fine");
        assertEquals(org.example.domain.constant.FitStatus.FAILED, job.getFitStatus());
        assertTrue(job.getFitError().contains("claude refused"));
    }

    @Test
    void aPostingThatFailsToReadIsKeptAsFailedWithTheReasonAndItsPdfIsCleanedUp() {
        var service = serviceWith(FakeJdExtractor.failing("could not read the PDF"));

        UUID id = acceptAndProcess(service, "junk".getBytes());

        PendingIntake failed = pendingIntakes.findById(id).orElseThrow();
        assertEquals(PendingIntakeStatus.FAILED, failed.getStatus());
        assertEquals("could not read the PDF", failed.getErrorMessage());
        assertFalse(Files.exists(Path.of(failed.getPdfPath())), "the raw PDF is cleaned up once terminally failed");
        assertEquals(0, jobs.count());
    }

    @Test
    void aPostingWithNoCompanyEndsUpFailedNotThrown() {
        var service = serviceWith(new FakeJdExtractor(fixture("", "Engineer", null, List.of("java"))));

        UUID id = acceptAndProcess(service, "x".getBytes());

        PendingIntake failed = pendingIntakes.findById(id).orElseThrow();
        assertEquals(PendingIntakeStatus.FAILED, failed.getStatus());
        assertTrue(failed.getErrorMessage().contains("company"), failed.getErrorMessage());
        assertEquals(0, jobs.count());
    }

    @Test
    void aPostingWithAnUnknownStackEndsUpFailed() {
        var service = serviceWith(new FakeJdExtractor(fixture("Acme", "Engineer", null, List.of("cobol"))));

        UUID id = acceptAndProcess(service, "x".getBytes());

        PendingIntake failed = pendingIntakes.findById(id).orElseThrow();
        assertTrue(failed.getErrorMessage().contains("cobol"), failed.getErrorMessage());
        assertEquals(0, jobs.count());
    }

    @Test
    void aPostingWithNoStackAtAllEndsUpFailed() {
        var service = serviceWith(new FakeJdExtractor(fixture("Acme", "Engineer", null, List.of())));

        UUID id = acceptAndProcess(service, "x".getBytes());

        assertEquals(PendingIntakeStatus.FAILED, pendingIntakes.findById(id).orElseThrow().getStatus());
        assertEquals(0, jobs.count());
    }

    @Test
    void processingAnAlreadyRemovedPendingRowIsANoOp() {
        var service = serviceWith(new FakeJdExtractor(fixture("Acme", "Engineer", null, List.of("java"))));

        assertDoesNotThrow(() -> service.processPending(UUID.randomUUID()));
        assertEquals(0, jobs.count());
    }

    // ---- Rule 1: job id given ---------------------------------------------------------------

    @Test
    void jobIdGivenAndMatchingSameCompanyIsSilentlyDiscardedAsACertainDuplicate() {
        var service = serviceWith(new FakeJdExtractor(fixture("Acme", "Engineer", "42", List.of("java"))));
        acceptAndProcess(service, "first".getBytes());

        // Same company + job id; role text even differs - rule 1 doesn't care about role.
        UUID secondId = acceptAndProcess(
                serviceWith(new FakeJdExtractor(fixture("Acme", "Senior Engineer", "42", List.of("java")))),
                "second".getBytes());

        assertEquals(1, jobs.count());
        assertTrue(pendingIntakes.findById(secondId).isEmpty(), "a plain duplicate is discarded, not kept as FAILED");
    }

    @Test
    void jobIdGivenAndNotMatchingIsDefinitelyNewEvenWithSameCompanyAndRoleWithinADay() {
        var service = serviceWith(new FakeJdExtractor(fixture("Acme", "Engineer", "1", List.of("java"))));
        acceptAndProcess(service, "first".getBytes());

        acceptAndProcess(serviceWith(new FakeJdExtractor(fixture("Acme", "Engineer", "2", List.of("java")))),
                "second".getBytes());

        assertEquals(2, jobs.count());
    }

    // ---- Rule 2: job id not given, checked against the single most recent job ---------------

    @Test
    void noJobIdAndSameCompanyAndRoleAsTheMostRecentJobIsADuplicate() {
        var service = serviceWith(new FakeJdExtractor(fixture("Acme", "Engineer", null, List.of("java"))));
        acceptAndProcess(service, "first".getBytes());

        acceptAndProcess(service, "second".getBytes());

        assertEquals(1, jobs.count());
        assertEquals(0, pendingIntakes.count());
    }

    @Test
    void noJobIdAndDifferentFromTheMostRecentJobIsNotCaughtByRule2() {
        acceptAndProcess(serviceWith(new FakeJdExtractor(fixture("Acme", "Engineer", null, List.of("java")))), "a".getBytes());
        // most recent job is now "Other / X" - unrelated to the next posting below
        acceptAndProcess(serviceWith(new FakeJdExtractor(fixture("Other", "X", null, List.of("java")))), "b".getBytes());

        // Same company+role as the FIRST job, but that's no longer the most recent, so rule 2 doesn't
        // fire on its own - rule 3 (ambiguous, within 24h) takes over instead.
        UUID thirdId = acceptAndProcess(serviceWith(new FakeJdExtractor(fixture("Acme", "Engineer", null, List.of("java")))),
                "c".getBytes());

        assertEquals(2, jobs.count());
        assertEquals(PendingIntakeStatus.NEEDS_CONFIRMATION, pendingIntakes.findById(thirdId).orElseThrow().getStatus());
    }

    // ---- Rule 3: ambiguous - resolved in the app, never automatically, never re-reading ------

    @Test
    void ambiguousPostingWaitsForConfirmationAndNeverReExtracts() {
        Job first = acceptResultingJob(serviceWith(new FakeJdExtractor(fixture("Acme", "Engineer", null, List.of("java")))), "a".getBytes());
        acceptAndProcess(serviceWith(new FakeJdExtractor(fixture("Other", "X", null, List.of("java")))), "b".getBytes());

        var extractionCount = new AtomicInteger();
        var service = serviceWith(new FakeJdExtractor(pdf -> {
            extractionCount.incrementAndGet();
            return fixture("Acme", "Engineer", null, List.of("java"));
        }));
        UUID pendingId = acceptAndProcess(service, "c".getBytes());

        PendingIntake ambiguous = pendingIntakes.findById(pendingId).orElseThrow();
        assertEquals(PendingIntakeStatus.NEEDS_CONFIRMATION, ambiguous.getStatus());
        assertEquals("Acme", ambiguous.getCompany());
        assertEquals(first.getId(), ambiguous.getSimilarJobId(), "should point at the EARLIER, similar job");
        assertEquals(2, jobs.count(), "nothing new is saved until confirmed");
        assertEquals(1, extractionCount.get());

        Job confirmed = service.confirmPending(pendingId);

        assertEquals(1, extractionCount.get(), "confirming must never re-read the (paid) posting");
        assertEquals(3, jobs.count());
        assertTrue(pendingIntakes.findById(pendingId).isEmpty());
        // same company/role/no job id as the earlier job - folder name must still be disambiguated
        // rather than silently colliding or being blocked.
        assertNotEquals(first.getFolderName(), confirmed.getFolderName());
        assertTrue(confirmed.getFolderName().startsWith(first.getFolderName()));
    }

    private Job acceptResultingJob(JobIntakeService service, byte[] bytes) {
        UUID id = acceptAndProcess(service, bytes);
        assertTrue(pendingIntakes.findById(id).isEmpty());
        return jobs.findAll().stream().reduce((a, b) -> b).orElseThrow();
    }

    @Test
    void decliningAnAmbiguousPostingDeletesItAndItsPdfAndSavesNothing() {
        acceptAndProcess(serviceWith(new FakeJdExtractor(fixture("Acme", "Engineer", null, List.of("java")))), "a".getBytes());
        acceptAndProcess(serviceWith(new FakeJdExtractor(fixture("Other", "X", null, List.of("java")))), "b".getBytes());
        var service = serviceWith(new FakeJdExtractor(fixture("Acme", "Engineer", null, List.of("java"))));
        UUID pendingId = acceptAndProcess(service, "c".getBytes());
        String pdfPath = pendingIntakes.findById(pendingId).orElseThrow().getPdfPath();

        service.rejectPending(pendingId);

        assertTrue(pendingIntakes.findById(pendingId).isEmpty());
        assertFalse(new File(pdfPath).exists());
        assertEquals(2, jobs.count());
    }

    @Test
    void confirmingAnUnknownOrAlreadyResolvedRowFails() {
        var service = serviceWith(new FakeJdExtractor(fixture("Acme", "Engineer", null, List.of("java"))));

        assertThrows(JobIntakeException.class, () -> service.confirmPending(UUID.randomUUID()));
    }

    @Test
    void confirmingAPendingRowThatIsNotAwaitingConfirmationFails() {
        var service = serviceWith(new FakeJdExtractor(fixture("Acme", "Engineer", null, List.of("java"))));
        PendingIntake stillPending = service.accept("x".getBytes()); // never processed - still just PENDING

        assertThrows(JobIntakeException.class, () -> service.confirmPending(stillPending.getId()));
    }

    @Test
    void rejectingAnUnknownRowFails() {
        var service = serviceWith(new FakeJdExtractor(fixture("Acme", "Engineer", null, List.of("java"))));

        assertThrows(JobIntakeException.class, () -> service.rejectPending(UUID.randomUUID()));
    }

    // ---- URL-based dedupe: caught in accept(), BEFORE anything is read ------------------------

    @Test
    void resubmittingTheSameUrlWhileStillPendingDoesNotQueueASecondCopy() {
        var extractionCount = new AtomicInteger();
        var service = serviceWith(new FakeJdExtractor(pdf -> {
            extractionCount.incrementAndGet();
            return fixture("Acme", "Engineer", null, List.of("java"));
        }));
        var first = service.accept("first".getBytes(), "https://example.com/job/1");
        assertInstanceOf(JobIntakeService.AcceptOutcome.New.class, first);

        var second = service.accept("second".getBytes(), "https://example.com/job/1");

        assertInstanceOf(JobIntakeService.AcceptOutcome.AlreadyQueued.class, second);
        assertEquals(((JobIntakeService.AcceptOutcome.New) first).pending().getId(),
                ((JobIntakeService.AcceptOutcome.AlreadyQueued) second).pending().getId());
        assertEquals(1, pendingIntakes.count(), "no second PendingIntake row, no second PDF saved");
        assertEquals(0, extractionCount.get(), "accept() never reads anything, dedupe or not");
    }

    @Test
    void resubmittingTheUrlOfAnAlreadyTrackedJobIsNeverReadAgain() {
        var service = serviceWith(new FakeJdExtractor(fixture("Acme", "Engineer", null, List.of("java"))));
        UUID pendingId = acceptAndProcessWithUrl(service, "first".getBytes(), "https://example.com/job/1");
        assertTrue(pendingIntakes.findById(pendingId).isEmpty());
        Job saved = jobs.findAll().stream().findFirst().orElseThrow();

        var extractionCount = new AtomicInteger();
        var again = serviceWith(new FakeJdExtractor(pdf -> {
            extractionCount.incrementAndGet();
            return fixture("Acme", "Engineer", null, List.of("java"));
        })).accept("second".getBytes(), "https://example.com/job/1");

        assertInstanceOf(JobIntakeService.AcceptOutcome.AlreadyTracked.class, again);
        assertEquals(saved.getId(), ((JobIntakeService.AcceptOutcome.AlreadyTracked) again).job().getId());
        assertEquals(0, extractionCount.get());
        assertEquals(1, jobs.count());
    }

    @Test
    void differentUrlsAreNeverTreatedAsTheSamePosting() {
        var service = serviceWith(new FakeJdExtractor(fixture("Acme", "Engineer", null, List.of("java"))));

        var first = service.accept("a".getBytes(), "https://example.com/job/1");
        var second = service.accept("b".getBytes(), "https://example.com/job/2");

        assertInstanceOf(JobIntakeService.AcceptOutcome.New.class, first);
        assertInstanceOf(JobIntakeService.AcceptOutcome.New.class, second);
        assertEquals(2, pendingIntakes.count());
    }

    @Test
    void noUrlAtAllNeverDedupes() {
        var service = serviceWith(new FakeJdExtractor(fixture("Acme", "Engineer", null, List.of("java"))));

        var first = service.accept("a".getBytes(), null);
        var second = service.accept("b".getBytes(), null);

        assertInstanceOf(JobIntakeService.AcceptOutcome.New.class, first);
        assertInstanceOf(JobIntakeService.AcceptOutcome.New.class, second);
        assertEquals(2, pendingIntakes.count());
    }

    @Test
    void aSavedJobRemembersItsUrl() {
        var service = serviceWith(new FakeJdExtractor(fixture("Acme", "Engineer", null, List.of("java"))));

        acceptAndProcessWithUrl(service, "a".getBytes(), "https://example.com/job/1");

        assertEquals("https://example.com/job/1", jobs.findAll().get(0).getUrl());
    }

    private UUID acceptAndProcessWithUrl(JobIntakeService service, byte[] pdfBytes, String url) {
        var accepted = (JobIntakeService.AcceptOutcome.New) service.accept(pdfBytes, url);
        service.processPending(accepted.pending().getId());
        return accepted.pending().getId();
    }
}
