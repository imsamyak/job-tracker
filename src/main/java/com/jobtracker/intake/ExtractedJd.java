package com.jobtracker.intake;

import java.util.List;

/**
 * What was understood from one job description. Optional fields are null when the posting does not say.
 *
 * @param isJobPosting false when the content is not a job posting at all (login page, article, ad...)
 * @param description  the posting's text; only filled by the extractor for images (text and links already have it)
 */
public record ExtractedJd(boolean isJobPosting, String company, String role, String location, String workMode,
                          String jobType, String experience, String salary, List<String> skills, String postingId,
                          String platform, String description) {}
