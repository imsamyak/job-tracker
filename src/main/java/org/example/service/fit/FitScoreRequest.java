package org.example.service.fit;

import java.util.List;

/** Everything the fit score is judged from - the job's own facts plus the candidate's real, fixed profile. */
public record FitScoreRequest(
        String company,
        String role,
        String jobDescription,
        List<String> stacks,
        List<String> skills,
        int experienceMinYears,
        /** Null when the posting gave no upper bound. */
        Integer experienceMaxYears,
        /** The candidate's own fixed background/highlights - see templates/coverletter/profile.txt (shared
         *  with the cover letter writer, one source of truth for "what's actually true about the candidate"). */
        String candidateProfile
) {
}
