package org.example.service.coverletter;

import java.util.List;

/**
 * Everything the letter's wording is written from - only real facts (the job's own description and the
 * candidate's actual, fixed profile text), so the writer has no reason to invent anything.
 */
public record CoverLetterRequest(
        String company,
        String role,
        String jobDescription,
        List<String> stacks,
        List<String> skills,
        /** The candidate's own fixed background/highlights - see templates/coverletter/profile.txt. */
        String candidateProfile
) {
}
