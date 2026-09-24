package org.example.controller;

import org.example.domain.constant.JobMode;
import org.example.domain.constant.JobStatus;
import org.example.domain.constant.JobType;
import org.example.domain.constant.PendingIntakeStatus;
import org.example.domain.embed.JobExperienceRequirement;
import org.example.domain.entity.Job;
import org.example.domain.entity.PendingIntake;
import org.example.respository.JobRepository;
import org.example.respository.PendingIntakeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Runs against an in-memory database; the real ./data file is never touched. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pending-controller-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jtraker.resume.schedule-initial-delay=PT1H",
        "jtraker.resume.schedule-delay=PT1H",
        "jtraker.intake.schedule-initial-delay=PT1H",
        "jtraker.intake.schedule-delay=PT1H"
})
class PendingIntakeControllerTest {

    @Autowired
    JobRepository jobs;

    @Autowired
    PendingIntakeRepository pendingIntakes;

    @Autowired
    PendingIntakeController controller;

    @BeforeEach
    void clean() {
        pendingIntakes.deleteAll();
        jobs.deleteAll();
    }

    @Test
    void aPlainPendingRowHasNoCompanyOrRoleYet() {
        pendingIntakes.save(PendingIntake.builder().pdfPath("x.pdf").build());

        var summary = controller.list().get(0);

        assertEquals("PENDING", summary.status());
        assertNull(summary.company());
        assertNull(summary.similarJob());
        // The scheduler is disabled in this test (1h delay) so nothing is ever actively being read.
        assertEquals(false, summary.active());
    }

    @Test
    void aNeedsConfirmationRowIncludesTheSimilarJobItResembles() {
        Job similar = jobs.save(Job.builder()
                .company("Acme").role("Engineer").folderName("Acme_Engineer")
                .type(JobType.FullTime).mode(JobMode.Remote)
                .experience(new JobExperienceRequirement(2, 5))
                .skills(List.of()).stacks(List.of("java")).contacts(List.of())
                .description("desc").status(JobStatus.RESUME_READY)
                .build());

        PendingIntake pending = PendingIntake.builder().pdfPath("x.pdf").build();
        pending.markNeedsConfirmation(null, "Acme", "Engineer", JobType.FullTime, JobMode.Remote, null, 2, 5,
                List.of(), List.of("java"), List.of(), "desc", similar.getId());
        pendingIntakes.save(pending);

        var summary = controller.list().get(0);

        assertEquals("NEEDS_CONFIRMATION", summary.status());
        assertEquals("Acme", summary.company());
        assertEquals(similar.getId().toString(), summary.similarJob().id());
        assertEquals("RESUME_READY", summary.similarJob().status());
    }

    @Test
    void aFailedRowIncludesTheReason() {
        PendingIntake pending = PendingIntake.builder().pdfPath("x.pdf").build();
        pending.markFailed("could not read the PDF");
        pendingIntakes.save(pending);

        var summary = controller.list().get(0);

        assertEquals(PendingIntakeStatus.FAILED.name(), summary.status());
        assertEquals("could not read the PDF", summary.errorMessage());
    }

    @Test
    void listedOldestFirst() {
        pendingIntakes.save(PendingIntake.builder().pdfPath("a.pdf")
                .createdAt(java.time.Instant.parse("2026-01-01T00:00:00Z")).build());
        pendingIntakes.save(PendingIntake.builder().pdfPath("b.pdf")
                .createdAt(java.time.Instant.parse("2026-01-02T00:00:00Z")).build());

        var list = controller.list();

        assertEquals(2, list.size());
        assertEquals(java.time.Instant.parse("2026-01-01T00:00:00Z"), list.get(0).createdAt());
        assertEquals(java.time.Instant.parse("2026-01-02T00:00:00Z"), list.get(1).createdAt());
    }
}
