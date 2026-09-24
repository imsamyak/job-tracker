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
import org.example.service.generator.ResumeGeneratorService;
import org.example.service.generator.ResumePdfRenderer;
import org.example.service.generator.SkillSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against an in-memory database. Builds its own {@link ReferralService} with a
 * {@link FakeCoverLetterWriter} rather than autowiring the real, Spring-wired cover letter writer - that
 * one talks to the real Claude CLI, which this test must never do (the resume side has no AI in it at
 * all, so the real {@link ResumeGeneratorService} is used as-is).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:referral-service-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jtraker.resume.schedule-initial-delay=PT1H",
        "jtraker.resume.schedule-delay=PT1H"
})
class ReferralServiceTest {

    private static final Path FONT_DIR = Path.of("C:/Windows/Fonts");

    @Autowired
    JobRepository jobs;

    @Autowired
    TransactionTemplate tx;

    @TempDir
    Path generatedRoot;

    private final ResumeGeneratorService resumeGenerator =
            new ResumeGeneratorService(SkillSelector.load(), new ResumePdfRenderer(FONT_DIR));

    @BeforeEach
    void clean() {
        jobs.deleteAll();
    }

    private Job save(String company) {
        Job job = Job.builder()
                .company(company).role("Engineer").folderName(company + "_Engineer")
                .type(JobType.FullTime).mode(JobMode.Remote)
                .experience(new JobExperienceRequirement(2, 5))
                .skills(List.of("Java")).stacks(List.of("java")).contacts(List.of())
                .description("Build backend services in Java with Kafka.").status(JobStatus.RESUME_READY)
                .build();
        return jobs.save(job);
    }

    private ReferralService serviceWith(FakeCoverLetterWriter writer) {
        var coverLetterGenerator = new CoverLetterGeneratorService(writer, new CoverLetterPdfRenderer(FONT_DIR));
        return new ReferralService(jobs, resumeGenerator, coverLetterGenerator, generatedRoot,
                new JobEventBroadcaster(), tx);
    }

    @Test
    void generateResumeAsyncMarksPendingBeforeAnyBackgroundWorkRuns() {
        Job job = save("Acme");
        ReferralService service = serviceWith(new FakeCoverLetterWriter(List.of("x")));

        service.generateResumeAsync(job.getId());

        assertEquals(CoverLetterStatus.PENDING, jobs.findById(job.getId()).orElseThrow().getReferralResumeStatus());
    }

    @Test
    void generateResumeWritesAPdfUnderAReferralSubfolderWithTheReferralEmail() throws Exception {
        Job job = save("Beta");
        ReferralService service = serviceWith(new FakeCoverLetterWriter(List.of("x")));

        service.generateResume(job.getId());

        Job reloaded = jobs.findById(job.getId()).orElseThrow();
        assertEquals(CoverLetterStatus.READY, reloaded.getReferralResumeStatus());
        assertNotNull(reloaded.getReferralResumePath());
        Path path = Path.of(reloaded.getReferralResumePath());
        assertTrue(Files.isRegularFile(path));
        assertTrue(path.toString().replace('\\', '/').contains("/referral/"));
    }

    @Test
    void generateCoverLetterWritesAPdfUnderAReferralSubfolder() throws Exception {
        Job job = save("Gamma");
        ReferralService service = serviceWith(new FakeCoverLetterWriter(List.of(
                "Opening paragraph.", "Middle paragraph.", "Closing paragraph.")));

        service.generateCoverLetter(job.getId());

        Job reloaded = jobs.findById(job.getId()).orElseThrow();
        assertEquals(CoverLetterStatus.READY, reloaded.getReferralCoverLetterStatus());
        Path path = Path.of(reloaded.getReferralCoverLetterPath());
        assertTrue(Files.isRegularFile(path));
        assertTrue(path.toString().replace('\\', '/').contains("/referral/"));
    }

    @Test
    void aCoverLetterWriterFailureMarksTheReferralCoverLetterFailed() {
        Job job = save("Delta");
        ReferralService service = serviceWith(FakeCoverLetterWriter.failing("claude refused"));

        service.generateCoverLetter(job.getId());

        Job reloaded = jobs.findById(job.getId()).orElseThrow();
        assertEquals(CoverLetterStatus.FAILED, reloaded.getReferralCoverLetterStatus());
        assertTrue(reloaded.getReferralCoverLetterError().contains("claude refused"));
    }
}
