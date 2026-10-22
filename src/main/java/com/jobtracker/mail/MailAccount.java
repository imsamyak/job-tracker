package com.jobtracker.mail;

import jakarta.persistence.*;
import java.time.Instant;

/** One Gmail account the app can read. The app password is kept locked, never as plain text. */
@Entity
@Table(name = "mail_accounts")
public class MailAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    /** Locked with SecretBox. */
    @Column(nullable = false, length = 500)
    private String lockedPassword;

    private Instant addedAt = Instant.now();

    /** When this account's mail was last read all the way through, or null before the first sync. */
    private Instant lastSyncedAt;

    /** Why the last sync could not read this account, or null when it went fine. */
    @Column(length = 500)
    private String lastError;

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getLockedPassword() { return lockedPassword; }
    public void setLockedPassword(String lockedPassword) { this.lockedPassword = lockedPassword; }
    public Instant getAddedAt() { return addedAt; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError == null || lastError.length() <= 480 ? lastError : lastError.substring(0, 480); }
}
