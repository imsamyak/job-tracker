package org.example.service;

import org.example.domain.constant.CoverLetterStatus;
import org.example.domain.constant.JobMode;
import org.example.domain.constant.JobStatus;
import org.example.domain.constant.JobType;
import org.example.domain.embed.JobExperienceRequirement;
import org.example.domain.entity.Job;
import org.example.respository.JobRepository;
import org.example.service.coverletter.CoverLetterGeneratorService;
import org.example.service.coverletter.CoverLetterPdfRenderer;
import org.example.service.coverletter.FakeCoverLetterWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against an in-memory database. Builds its own {@link CoverLetterService} with a
 * {@link FakeCoverLetterWriter} rather than autowiring the real, Spring-wired one - that one talks to the
 * real Claude CLI, which this test must never do. {@link CoverLetterService#generate} is called directly
 * (package-private, for exactly this reason) rather than {@code generateAsync}, so results are checked
 * synchronously instead of polling a background thread.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:coverletter-service-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jtraker.resume.schedule-initial-delay=PT1H",
        "jtraker.resume.schedule-delay=PT1H"
})
class CoverLetterServiceTest {

    private static final Path FONT_DIR = Path.of("C:/Windows/Fonts");

    @Autowired
    JobRepository jobs;

    @Autowired
    TransactionTemplate tx;

    @TempDir
    Path generatedRoot;

    @BeforeEach
    void clean() {
        jobs.deleteAll();
    }

    private Job save(String company) {
        Job job = Job.builder()
                .company(company)
                .role("Engineer")
                .folderName(company + "_Engineer")
                .type(JobType.FullTime)
                .mode(JobMode.Remote)
                .experience(new JobExperienceRequirement(2, 5))
                .skills(List.of("Java"))
                .stacks(List.of("java"))
                .contacts(List.of())
                .description("Build backend services in Java with Kafka.")
                .status(JobStatus.RESUME_READY)
                .build();
        return jobs.save(job);
    }

    private CoverLetterService serviceWith(FakeCoverLetterWriter writer) {
        var generator = new CoverLetterGeneratorService(writer, new CoverLetterPdfRenderer(FONT_DIR));
        return new CoverLetterService(jobs, generator, generatedRoot, new JobEventBroadcaster(), tx);
    }

    @Test
    void generateAsyncMarksTheJobPendingBeforeAnyBackgroundWorkRuns() {
        Job job = save("Acme");
        // A writer that would hang if actually invoked - proves generateAsync's synchronous part (marking
        // PENDING) doesn't wait on it.
        CoverLetterService service = serviceWith(new FakeCoverLetterWriter(r -> {
            throw new AssertionError("must not run synchronously from generateAsync");
        }));

        service.generateAsync(job.getId());

        assertEquals(CoverLetterStatus.PENDING, jobs.findById(job.getId()).orElseThrow().getCoverLetterStatus());
    }

    @Test
    void generateWritesAndRendersACoverLetterPdf() {
        Job job = save("Beta");
        CoverLetterService service = serviceWith(new FakeCoverLetterWriter(List.of(
                "I'm excited to apply for this role at Beta given my backend experience.",
                "At Compro Technologies I built event-driven services on AWS that match what this role needs.",
                "I'd welcome the chance to discuss how I can contribute.")));

        service.generate(job.getId());

        Job reloaded = jobs.findById(job.getId()).orElseThrow();
        assertEquals(CoverLetterStatus.READY, reloaded.getCoverLetterStatus());
        assertNull(reloaded.getCoverLetterError());
        assertNotNull(reloaded.getCoverLetterPath());
        assertTrue(java.nio.file.Files.isRegularFile(Path.of(reloaded.getCoverLetterPath())),
                "the PDF must actually exist on disk at the recorded path");
    }

    @Test
    void aWriterFailureMarksTheJobFailedWithTheReason() {
        Job job = save("Gamma");
        CoverLetterService service = serviceWith(FakeCoverLetterWriter.failing("claude refused"));

        service.generate(job.getId());

        Job reloaded = jobs.findById(job.getId()).orElseThrow();
        assertEquals(CoverLetterStatus.FAILED, reloaded.getCoverLetterStatus());
        assertTrue(reloaded.getCoverLetterError().contains("claude refused"));
        assertNull(reloaded.getCoverLetterPath());
    }

    @Test
    void generateOnAnUnknownIdDoesNotThrow() {
        CoverLetterService service = serviceWith(new FakeCoverLetterWriter(List.of("x")));

        service.generate(UUID.randomUUID()); // must be a no-op, not an exception
    }
}
