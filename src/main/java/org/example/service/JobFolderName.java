package org.example.service;

import java.util.regex.Pattern;

/** Builds the [Company]_[Role]_[JobId] name used as a job's identity and folder. */
public final class JobFolderName {

    private static final Pattern ILLEGAL = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final int MAX_PART_LENGTH = 60;

    private JobFolderName() {
    }

    /** @param jobId the posting's own id; blank or null leaves that part out */
    public static String of(String company, String role, String jobId) {
        String name = clean(company) + "_" + clean(role);
        String id = jobId == null ? "" : clean(jobId);
        return id.isEmpty() ? name : name + "_" + id;
    }

    private static String clean(String text) {
        String cleaned = ILLEGAL.matcher(text == null ? "" : text).replaceAll(" ");
        cleaned = WHITESPACE.matcher(cleaned.trim()).replaceAll("-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[-.]+|[-.]+$", "");
        return cleaned.length() > MAX_PART_LENGTH ? cleaned.substring(0, MAX_PART_LENGTH) : cleaned;
    }
}
