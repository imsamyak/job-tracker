package org.example.service;

import org.example.domain.entity.Job;
import org.example.service.fit.FitScoreCalculator;
import org.example.service.fit.FitScoreRequest;

/**
 * Shared by {@code JobIntakeService} (scores a job the moment it's saved) and
 * {@code FitScoreBackfillRunner} (catches anything that was never scored, e.g. jobs that existed before
 * this feature, or whose scoring failed and needs a retry) - one place for the actual calculate-and-mark
 * logic so both callers behave identically.
 */
public class FitScoreService {

    private final FitScoreCalculator calculator;

    public FitScoreService(FitScoreCalculator calculator) {
        this.calculator = calculator;
    }

    /**
     * Mutates {@code job} in place (markFitReady/markFitFailed) - does not save it; the caller decides
     * when/how to persist. A failure here is caught and recorded on the job, never thrown - scoring must
     * never be the reason a job fails to save.
     */
    public void score(Job job) {
        try {
            var result = calculator.calculate(new FitScoreRequest(job.getCompany(), job.getRole(),
                    job.getDescription(), job.getStacks(), job.getSkills(), job.getExperience().getMin(),
                    job.getExperience().getMax(), CandidateProfile.text()));
            job.markFitReady(result.percentage(), result.summary());
        } catch (RuntimeException e) {
            job.markFitFailed(e.getMessage());
        }
    }
}
