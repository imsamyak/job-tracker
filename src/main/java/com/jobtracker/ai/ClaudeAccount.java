package com.jobtracker.ai;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Which Claude login the {@code claude} program on this computer is using. The app has no Claude password or
 * key of its own: it runs that program, so it works on whatever that program is signed in to.
 */
@Service
public class ClaudeAccount {

    /**
     * @param installed the claude program could be started
     * @param loggedIn  it is signed in
     * @param method    "subscription" (a Claude plan), "api" (Console key, billed per use) or null when not signed in
     */
    public record Status(boolean installed, boolean loggedIn, String method, String email) {}

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final File NUL_DEVICE =
            new File(System.getProperty("os.name", "").toLowerCase().contains("win") ? "NUL" : "/dev/null");

    private final ClaudeCli cli;
    private Process login;

    public ClaudeAccount(ClaudeCli cli) {
        this.cli = cli;
    }

    public Status status() {
        try {
            Process p = new ProcessBuilder(cli.executable().toString(), "auth", "status", "--json")
                    .redirectInput(ProcessBuilder.Redirect.from(NUL_DEVICE)).redirectErrorStream(true).start();
            byte[] out = p.getInputStream().readNBytes(100_000);
            if (!p.waitFor(20, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new Status(true, false, null, null);
            }
            return parse(new String(out, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new Status(false, false, null, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Status(true, false, null, null);
        }
    }

    /** Package-private for tests. Anything unreadable counts as signed out. */
    static Status parse(String json) {
        try {
            JsonNode n = JSON.readTree(json);
            if (!n.path("loggedIn").asBoolean(false)) return new Status(true, false, null, null);
            String method = "api_key".equals(n.path("authMethod").asText("")) ? "api" : "subscription";
            String email = n.path("email").asText("");
            return new Status(true, true, method, email.isEmpty() ? null : email);
        } catch (Exception e) {
            return new Status(true, false, null, null);
        }
    }

    /**
     * Starts {@code claude auth login --claudeai}, which opens the browser on this computer so you can sign in with
     * your Claude plan. It runs in the background; the screen checks {@link #status()} until it changes.
     * Returns false when one is already waiting.
     */
    public synchronized boolean startLogin() throws IOException {
        if (login != null && login.isAlive()) return false;
        login = new ProcessBuilder(cli.executable().toString(), "auth", "login", "--claudeai")
                .redirectInput(ProcessBuilder.Redirect.from(NUL_DEVICE))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        Process started = login;
        // an abandoned sign-in must not wait forever
        Thread.ofVirtual().start(() -> {
            try {
                if (!started.waitFor(5, TimeUnit.MINUTES)) started.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        return true;
    }
}
