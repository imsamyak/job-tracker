package org.example.service;

import org.example.domain.constant.JobMode;
import org.example.domain.constant.JobStatus;
import org.example.domain.constant.JobType;
import org.example.domain.embed.JobExperienceRequirement;
import org.example.domain.entity.Job;
import org.example.respository.JobRepository;
import org.example.service.help.FakeJobHelpWriter;
import org.example.service.help.JobHelpWriteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against an in-memory database. Builds its own {@link JobHelpService} with a
 * {@link FakeJobHelpWriter} rather than autowiring the real, Spring-wired one - that one talks to the real
 * Claude CLI, which this test must never do. The job is read inside a transaction (same as
 * {@link JobController#help}) so its lazy stacks/skills are loaded before {@link JobHelpService#answer}
 * touches them.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:job-help-service-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jtraker.resume.schedule-initial-delay=PT1H",
        "jtraker.resume.schedule-delay=PT1H"
})
class JobHelpServiceTest {

    @Autowired
    JobRepository jobs;

    @Autowired
    TransactionTemplate tx;

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

    @Test
    void answerReturnsWhatTheWriterProduces() {
        JobHelpService service = new JobHelpService(new FakeJobHelpWriter("I'm excited about this role because..."));
        Job job = save("Acme");

        String answer = tx.execute(status -> service.answer(jobs.findById(job.getId()).orElseThrow(),
                "Why do you want to work here?"));

        assertEquals("I'm excited about this role because...", answer);
    }

    @Test
    void answerPassesTheJobsFactsAndTheQuestionThrough() {
        JobHelpService service = new JobHelpService(new FakeJobHelpWriter(r -> {
            assertEquals("Acme", r.company());
            assertEquals("Engineer", r.role());
            assertTrue(r.skills().contains("Java"));
            assertEquals("What's your notice period?", r.question());
            return "Two weeks.";
        }));
        Job job = save("Acme");

        String answer = tx.execute(status -> service.answer(jobs.findById(job.getId()).orElseThrow(),
                "What's your notice period?"));

        assertEquals("Two weeks.", answer);
    }

    @Test
    void aWriterFailurePropagates() {
        JobHelpService service = new JobHelpService(FakeJobHelpWriter.failing("claude refused"));
        Job job = save("Beta");

        assertThrows(JobHelpWriteException.class, () -> tx.execute(status ->
                service.answer(jobs.findById(job.getId()).orElseThrow(), "Anything?")));
    }
}
