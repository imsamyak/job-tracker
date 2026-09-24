package org.example.domain.entity;

import org.example.domain.constant.FitStatus;
import org.example.domain.constant.JobMode;
import org.example.domain.constant.JobStatus;
import org.example.domain.constant.JobType;
import org.example.domain.embed.JobExperienceRequirement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobTest {

    private Job job() {
        return Job.builder()
                .company("Acme").role("Engineer").folderName("Acme_Engineer")
                .type(JobType.FullTime).mode(JobMode.Remote)
                .experience(new JobExperienceRequirement(2, 5))
                .skills(List.of()).stacks(List.of("java")).contacts(List.of())
                .description("desc").status(JobStatus.NEW)
                .build();
    }

    @Test
    void markFitReadyTruncatesAnOverLongSummarySoItNeverBreaksTheSave() {
        Job job = job();
        String tooLong = "x".repeat(1500);

        job.markFitReady(72, tooLong);

        assertEquals(FitStatus.READY, job.getFitStatus());
        assertEquals(72, job.getFitPercentage());
        assertTrue(job.getFitSummary().length() <= 1000, "must fit the fit_summary column (1000 chars)");
    }

    @Test
    void markFitReadyClampsThePercentageToZeroToHundred() {
        Job job = job();

        job.markFitReady(150, "x");
        assertEquals(100, job.getFitPercentage());

        job.markFitReady(-20, "x");
        assertEquals(0, job.getFitPercentage());
    }
}
