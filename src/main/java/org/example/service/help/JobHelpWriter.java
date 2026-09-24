package org.example.service.help;

/**
 * Answers one free-form question about a job application on the candidate's behalf - e.g. a question
 * lifted straight out of a company's application form. The real implementation asks Claude; tests use a
 * fake so they never shell out or spend money.
 */
public interface JobHelpWriter {

    /**
     * @return the answer, ready to paste into the form as-is
     * @throws JobHelpWriteException if Claude cannot be run, refuses, exceeds budget, or does not return
     *                                usable structured output
     */
    String answer(JobHelpRequest request);
}
