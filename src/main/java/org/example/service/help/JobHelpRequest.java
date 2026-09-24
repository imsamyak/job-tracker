package org.example.service.help;

import java.util.List;

/**
 * Everything a "help me answer this application-form question" call needs - the job's own facts plus the
 * candidate's fixed real background (see {@code CandidateProfile}), so Claude has no reason to invent
 * anything, and the person's actual question (e.g. "Why do you want to work here?", pasted straight out of
 * an application form).
 */
public record JobHelpRequest(
        String company,
        String role,
        String jobDescription,
        List<String> stacks,
        List<String> skills,
        String candidateProfile,
        String question
) {
}
