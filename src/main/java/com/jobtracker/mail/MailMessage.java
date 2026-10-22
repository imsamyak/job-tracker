package com.jobtracker.mail;

import java.time.Instant;

/** One email, reduced to what deciding about a job application needs. */
public record MailMessage(String messageId, String from, String subject, Instant receivedAt, String text) {}
