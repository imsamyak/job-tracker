package org.example.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The candidate's own fixed, real background - the one source of truth fed to every Claude call that
 * writes something ABOUT the candidate (the cover letter, the fit score), so none of them can invent
 * experience beyond what's actually here. Shared rather than duplicated per-caller.
 */
public final class CandidateProfile {

    private static final String RESOURCE = "/templates/coverletter/profile.txt";
    private static final String TEXT = read();

    private CandidateProfile() {
    }

    public static String text() {
        return TEXT;
    }

    private static String read() {
        try (InputStream in = CandidateProfile.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
