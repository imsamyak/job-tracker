package org.example.service;

import lombok.extern.slf4j.Slf4j;
import org.example.domain.entity.Job;
import org.example.service.help.JobHelpRequest;
import org.example.service.help.JobHelpWriter;
import org.springframework.stereotype.Component;

/**
 * Answers one free-form question about a job application on the candidate's behalf - e.g. something lifted
 * straight out of a company's application form. Unlike the cover letter/referral docs, this is
 * synchronous: it's an interactive back-and-forth (the person may ask several different questions about the
 * same job in a row), not a single generated artifact worth a PENDING/READY status on the job itself, so
 * there's nothing to persist here - the answer is returned directly in the response.
 */
@Slf4j
@Component
public class JobHelpService {

    private final JobHelpWriter writer;

    public JobHelpService(JobHelpWriter writer) {
        this.writer = writer;
    }

    /**
     * Caller must have {@code job}'s lazy {@code stacks}/{@code skills} collections already loaded (i.e.
     * called from within the same transaction that fetched it) - same requirement as
     * {@code CoverLetterGeneratorService.generate}.
     */
    public String answer(Job job, String question) {
        JobHelpRequest request = new JobHelpRequest(job.getCompany(), job.getRole(), job.getDescription(),
                job.getStacks(), job.getSkills(), CandidateProfile.text(), question);
        return writer.answer(request);
    }
}
