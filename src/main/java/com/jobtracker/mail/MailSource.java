package com.jobtracker.mail;

import java.time.Instant;
import java.util.List;

/** Reads recent mail from one mailbox. Swappable so tests need no Gmail. */
public interface MailSource {

    /** The newest {@code max} messages received on or after {@code since}, oldest first. Nothing is changed in the mailbox. */
    List<MailMessage> fetchSince(String email, String appPassword, Instant since, int max, Progress progress) throws MailVerifier.MailException;

    /** Told how many emails have been downloaded out of how many there are, so a long read can show where it is. */
    @FunctionalInterface
    interface Progress {
        void update(int done, int total);
    }
}
