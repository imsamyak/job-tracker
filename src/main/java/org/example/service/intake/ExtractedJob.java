package org.example.service.intake;

import java.util.List;

/**
 * A job posting as extracted from its PDF/text by a {@link JdExtractor}. Field values are whatever the
 * extractor produced and are not yet validated; {@link JobIntakeService} does that before saving.
 */
public record ExtractedJob(
        String company,
        String role,
        /** The company's own posting id, or null when the posting has none. */
        String jobId,
        /** Expected to name a {@link org.example.domain.constant.JobType} constant. */
        String type,
        /** Expected to name a {@link org.example.domain.constant.JobMode} constant. */
        String mode,
        /** City/region the posting names, or null when it doesn't say (e.g. a fully remote posting). */
        String location,
        int experienceMinYears,
        /** Null when the posting gives no upper bound. */
        Integer experienceMaxYears,
        /** Stacks for the resume (react, vue, node, java, cloud), primary first. */
        List<String> stacks,
        /** Skill keywords mentioned in the posting, in the posting's own wording. */
        List<String> skills,
        List<String> contacts,
        /** The full job description, cleaned of page furniture (nav, ads, "similar jobs", ...). */
        String description
) {
}
