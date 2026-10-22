package com.jobtracker.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the mail sync on its own, every few minutes, so statuses stay current without you doing anything. */
@Component
public class MailSyncWorker {

    private static final Logger log = LoggerFactory.getLogger(MailSyncWorker.class);

    private final MailSyncService sync;

    public MailSyncWorker(MailSyncService sync) {
        this.sync = sync;
    }

    /** At start-up: jobs added from Gmail earlier get their link to the email, so "View mail" works on them at once. */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onReady() {
        try {
            sync.linkOlderJobsToTheirMail();
        } catch (RuntimeException e) {
            log.warn("Could not link older jobs to their mail", e);
        }
    }

    @Scheduled(fixedDelayString = "${jobtracker.mail.sync-ms:600000}", initialDelayString = "${jobtracker.mail.initial-delay-ms:60000}")
    public void run() {
        try {
            sync.syncAll();
        } catch (RuntimeException e) {
            log.warn("Mail sync run failed", e);
        }
    }
}
