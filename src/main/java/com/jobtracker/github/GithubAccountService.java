package com.jobtracker.github;

import com.jobtracker.secret.SecretBox;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Keeps the GitHub token (locked) and hands it out to the parts of the app that call GitHub. */
@Service
public class GithubAccountService {

    private final GithubAccountRepository repo;
    private final GithubIdentity identity;
    private final SecretBox box;

    public GithubAccountService(GithubAccountRepository repo, GithubIdentity identity, SecretBox box) {
        this.repo = repo;
        this.identity = identity;
        this.box = box;
    }

    public Optional<GithubAccount> current() {
        return repo.findById(GithubAccount.THE_ONE);
    }

    /** The token to send to GitHub, or null when not connected. */
    public String token() {
        return current().map(a -> box.unlock(a.getLockedToken())).orElse(null);
    }

    /** Checks the token with GitHub, then keeps it. Connecting again replaces the earlier account. */
    public GithubAccount connect(String token) throws GithubIdentity.TokenException {
        String login = identity.loginFor(token);
        GithubAccount a = current().orElseGet(GithubAccount::new);
        a.setLogin(login);
        a.setLockedToken(box.lock(token));
        a.setConnectedAt(Instant.now());
        return repo.save(a);
    }

    public void disconnect() {
        repo.deleteById(GithubAccount.THE_ONE);
    }
}
