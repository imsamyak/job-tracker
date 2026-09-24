package org.example.service;

import org.example.domain.constant.JobMode;
import org.example.domain.constant.JobStatus;
import org.example.domain.constant.JobType;
import org.example.domain.embed.JobExperienceRequirement;
import org.example.domain.entity.Job;
import org.example.respository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against an in-memory database, so the real ./data file is never touched. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:scheduler-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.generated.path=target/test-output/generated",
        "jtraker.resume.schedule-initial-delay=PT1H",
        "jtraker.resume.schedule-delay=PT1H"
})
class ResumeSchedulerTest {

    @Autowired
    JobRepository jobs;

    @Autowired
    ResumeScheduler scheduler;

    @BeforeEach
    void clean() {
        jobs.deleteAll();
    }

    private Job job(String company, String role, String jobId, List<String> stacks) {
        return Job.builder()
                .company(company)
                .role(role)
                .jobId(jobId)
                .folderName(JobFolderName.of(company, role, jobId))
                .type(JobType.FullTime)
                .mode(JobMode.Remote)
                .experience(new JobExperienceRequirement(2, 5))
                .skills(List.of("Java", "Kafka"))
                .stacks(stacks)
                .description("Build backend services in Java with Kafka.")
                .build();
    }

    @Test
    void generatesTheResumeAndMarksTheJobReady() {
        jobs.save(job("Acme Corp", "Backend Engineer", "42", List.of("java")));

        scheduler.generatePending();

        Job saved = jobs.findByFolderName("Acme-Corp_Backend-Engineer_42").orElseThrow();
        assertEquals(JobStatus.RESUME_READY, saved.getStatus());
        assertNull(saved.getLastError());
        Path resume = Path.of(saved.getResumePath());
        assertEquals("Resume_Samyak_Jain.pdf", resume.getFileName().toString());
        assertTrue(Files.isRegularFile(resume));
        assertEquals("Acme-Corp_Backend-Engineer_42", resume.getParent().getFileName().toString());
    }

    @Test
    void generatesManyResumesAtTheSameTime() {
        IntStream.range(0, 8).forEach(i -> jobs.save(job("Company" + i, "Engineer", null, List.of("node", "react"))));

        scheduler.generatePending();

        assertEquals(8, jobs.findAll().stream().filter(j -> j.getStatus() == JobStatus.RESUME_READY).count());
        jobs.findAll().forEach(j -> assertTrue(Files.isRegularFile(Path.of(j.getResumePath()))));
    }

    @Test
    void marksAJobFailedWithTheReasonAndRequeuesItOnRestart() {
        jobs.save(job("Broken Inc", "Engineer", null, List.of()));

        scheduler.generatePending();

        Job failed = jobs.findByFolderName("Broken-Inc_Engineer").orElseThrow();
        assertEquals(JobStatus.FAILED, failed.getStatus());
        assertTrue(failed.getLastError().contains("At least one stack"), failed.getLastError());

        scheduler.requeueFailedJobs();
        assertEquals(JobStatus.NEW, jobs.findByFolderName("Broken-Inc_Engineer").orElseThrow().getStatus());
    }

    @Test
    void neverStoresTheSameFolderTwice() {
        jobs.save(job("Acme", "Engineer", null, List.of("java")));

        assertThrows(DataIntegrityViolationException.class,
                () -> jobs.saveAndFlush(job("Acme", "Engineer", null, List.of("java"))));
    }
}
