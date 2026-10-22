package com.jobtracker.github;

import jakarta.persistence.*;
import java.time.Instant;

/** The one GitHub account the app is connected to. The token is kept locked, never as plain text. */
@Entity
@Table(name = "github_account")
public class GithubAccount {
    /** There is only ever one, so its id never changes. */
    static final long THE_ONE = 1L;

    @Id
    private Long id = THE_ONE;

    @Column(nullable = false)
    private String login;

    /** Locked with SecretBox. */
    @Column(nullable = false, length = 1000)
    private String lockedToken;

    private Instant connectedAt = Instant.now();

    public Long getId() { return id; }
    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public String getLockedToken() { return lockedToken; }
    public void setLockedToken(String lockedToken) { this.lockedToken = lockedToken; }
    public Instant getConnectedAt() { return connectedAt; }
    public void setConnectedAt(Instant connectedAt) { this.connectedAt = connectedAt; }
}
