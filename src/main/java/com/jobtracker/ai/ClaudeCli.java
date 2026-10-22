package com.jobtracker.ai;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one place that talks to Claude: the Claude Code CLI ({@code claude -p}), which runs on the
 * user's own subscription rather than a metered API key. Every AI feature goes through here, so the
 * concurrency cap, the budget cap and the usage log are shared.
 *
 * Text goes in, JSON comes out. Text-only calls have no tools at all. A call that must look at an
 * image gets exactly one tool, Read, limited to one folder, so a hostile page or screenshot can at
 * most make one answer odd; it can never run a command or open another file.
 */
@Component
public class ClaudeCli {

    /** Claude could not be run or did not give a usable answer. */
    public static class AiException extends Exception {
        public AiException(String message) {
            super(message);
        }

        public AiException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(ClaudeCli.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_STREAM_BYTES = 2_000_000;
    private static final File NUL_DEVICE =
            new File(System.getProperty("os.name", "").toLowerCase().contains("win") ? "NUL" : "/dev/null");

    private final Path executable;
    private final String model;
    private final double maxBudgetUsd;
    private final Duration timeout;
    private final Semaphore gate;
    private final ExecutorService streamReaders = Executors.newCachedThreadPool();

    public ClaudeCli(@Value("${jobtracker.claude.executable:${user.home}/.local/bin/claude.exe}") Path executable,
                     @Value("${jobtracker.claude.model:claude-haiku-4-5-20251001}") String model,
                     @Value("${jobtracker.claude.max-budget-usd:0.50}") double maxBudgetUsd,
                     @Value("${jobtracker.claude.timeout:PT3M}") Duration timeout,
                     @Value("${jobtracker.claude.max-parallel:3}") int maxParallel) {
        this.executable = executable;
        this.model = model;
        this.maxBudgetUsd = maxBudgetUsd;
        this.timeout = timeout;
        this.gate = new Semaphore(maxParallel);
    }

    /** The claude program this app runs. */
    public Path executable() {
        return executable;
    }

    /** Text in, JSON out. No tools. */
    public JsonNode run(String feature, String prompt, String jsonSchema) throws AiException {
        return run(feature, prompt, jsonSchema, null);
    }

    /**
     * Same, but Claude may also read files inside {@code readDir} (used to look at an image the prompt
     * names). At most {@code max-parallel} calls run at once; the rest wait their turn.
     */
    public JsonNode run(String feature, String prompt, String jsonSchema, Path readDir) throws AiException {
        try {
            gate.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("Interrupted while waiting for a free Claude slot", e);
        }
        try {
            return runNow(feature, prompt, jsonSchema, readDir);
        } finally {
            gate.release();
        }
    }

    private JsonNode runNow(String feature, String prompt, String jsonSchema, Path readDir) throws AiException {
        // Java on Windows passes an argument without spaces through unquoted and does not escape an
        // embedded '"', so the child's own parser would strip every quote of the schema. Escaping
        // '"' as \" survives it. Backslashes must stay untouched.
        List<String> command = new ArrayList<>(List.of(
                executable.toString(), "-p", winArg(prompt),
                "--output-format", "json",
                "--json-schema", winArg(jsonSchema),
                "--model", model,
                "--max-budget-usd", String.valueOf(maxBudgetUsd)));
        if (readDir != null) {
            command.addAll(List.of("--tools", "Read", "--add-dir", winArg(readDir.toAbsolutePath().toString())));
        }
        command.addAll(List.of("--permission-prompts", "none", "--strict-mcp-config", "--no-session-persistence"));

        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectInput(ProcessBuilder.Redirect.from(NUL_DEVICE));
            // Extraction is mechanical; hidden "thinking" tokens would only add cost.
            builder.environment().put("MAX_THINKING_TOKENS", "0");
            process = builder.start();
        } catch (IOException e) {
            throw new AiException("Could not start claude (" + executable + "): " + e.getMessage(), e);
        }

        Future<byte[]> stdout = streamReaders.submit(() -> readLimited(process.getInputStream()));
        Future<byte[]> stderr = streamReaders.submit(() -> readLimited(process.getErrorStream()));

        boolean finished;
        try {
            finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new AiException("Interrupted while waiting for claude", e);
        }
        if (!finished) {
            process.destroyForcibly();
            throw new AiException("claude did not finish within " + timeout);
        }

        String out = collect(stdout);
        if (process.exitValue() != 0) {
            String err = collect(stderr);
            log.warn("claude exited {} - stderr: {}", process.exitValue(), err);
            // The CLI usually explains itself in its JSON on stdout ("Credit balance is too low", "Not logged in"...).
            String why = err.isBlank() ? "" : shorten(err);
            try {
                String result = JSON.readTree(out).path("result").asText("");
                if (!result.isBlank()) why = shorten(result);
            } catch (Exception ignored) {
                // not JSON: keep stderr
            }
            throw new AiException("claude exited with code " + process.exitValue() + (why.isBlank() ? "" : ": " + why));
        }

        JsonNode reply;
        try {
            reply = JSON.readTree(out);
        } catch (Exception e) {
            throw new AiException("claude did not return valid JSON: " + e.getMessage(), e);
        }
        UsageLog.record(feature, reply);

        if (reply.path("is_error").asBoolean(false)) {
            List<String> errors = new ArrayList<>();
            reply.path("errors").forEach(n -> errors.add(n.asText()));
            String result = reply.path("result").asText("");
            throw new AiException("claude reported an error: " + (errors.isEmpty() ? shorten(result) : errors));
        }
        JsonNode structured = reply.get("structured_output");
        if (structured == null || structured.isNull()) {
            throw new AiException("claude returned no structured_output");
        }
        return structured;
    }

    private static String winArg(String value) {
        return value.replace("\"", "\\\"");
    }

    private static String shorten(String s) {
        String t = s.strip();
        return t.length() > 300 ? t.substring(0, 300) + "..." : t;
    }

    private static byte[] readLimited(InputStream in) {
        try {
            return in.readNBytes(MAX_STREAM_BYTES);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String collect(Future<byte[]> f) {
        try {
            return new String(f.get(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "(could not read: " + e.getMessage() + ")";
        }
    }
}
