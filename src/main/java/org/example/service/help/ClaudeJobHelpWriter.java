package org.example.service.help;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

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
import java.util.concurrent.TimeUnit;

/**
 * Answers an application-form question with the Claude Code CLI ({@code claude -p}) - same billing model
 * and CLI-invocation approach as {@code ClaudeCoverLetterWriter}/{@code ClaudeCliJdExtractor}.
 */
@Slf4j
public class ClaudeJobHelpWriter implements JobHelpWriter {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_STREAM_BYTES = 2_000_000;
    private static final int MAX_DESCRIPTION_CHARS = 6000;
    private static final int MAX_QUESTION_CHARS = 4000;

    private final Path executable;
    private final String model;
    private final double maxBudgetUsd;
    private final Duration timeout;
    private final ExecutorService streamReaders = Executors.newCachedThreadPool();

    public ClaudeJobHelpWriter(Path executable, String model, double maxBudgetUsd, Duration timeout) {
        this.executable = executable;
        this.model = model;
        this.maxBudgetUsd = maxBudgetUsd;
        this.timeout = timeout;
    }

    @Override
    public String answer(JobHelpRequest request) {
        String prompt = buildPrompt(request);
        String schema = buildSchema();

        // Same Windows ProcessBuilder quoting fix as ClaudeCoverLetterWriter - see that class for why.
        List<String> command = List.of(
                executable.toString(), "-p", winArg(prompt),
                "--output-format", "json",
                "--json-schema", winArg(schema),
                "--model", model,
                "--max-budget-usd", String.valueOf(maxBudgetUsd),
                "--permission-prompts", "none",
                "--strict-mcp-config",
                "--no-session-persistence");

        JsonNode result = runClaude(command);
        String answer = result.hasNonNull("answer") ? result.get("answer").asText() : null;
        if (answer == null || answer.isBlank()) {
            throw new JobHelpWriteException("claude returned no answer");
        }
        return answer.strip();
    }

    private JsonNode runClaude(List<String> command) {
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .redirectInput(ProcessBuilder.Redirect.from(NUL_DEVICE))
                    .start();
        } catch (IOException e) {
            throw new JobHelpWriteException("Could not start claude: " + e.getMessage(), e);
        }

        Future<byte[]> stdout = streamReaders.submit(() -> readLimited(process.getInputStream()));
        Future<byte[]> stderr = streamReaders.submit(() -> readLimited(process.getErrorStream()));

        boolean finished;
        try {
            finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new JobHelpWriteException("Interrupted while waiting for claude", e);
        }
        if (!finished) {
            process.destroyForcibly();
            throw new JobHelpWriteException("claude did not finish within " + timeout);
        }

        String out = collect(stdout);
        if (process.exitValue() != 0) {
            log.warn("claude exited {} - stderr: {}", process.exitValue(), collect(stderr));
            throw new JobHelpWriteException("claude exited with code " + process.exitValue());
        }

        JsonNode reply;
        try {
            reply = JSON.readTree(out);
        } catch (Exception e) {
            throw new JobHelpWriteException("claude did not return valid JSON: " + e.getMessage(), e);
        }
        org.example.service.UsageLog.record("help", reply);

        if (reply.path("is_error").asBoolean(false)) {
            List<String> errors = new ArrayList<>();
            reply.path("errors").forEach(n -> errors.add(n.asText()));
            throw new JobHelpWriteException("claude reported an error: " + errors);
        }
        JsonNode structured = reply.get("structured_output");
        if (structured == null || structured.isNull()) {
            throw new JobHelpWriteException("claude returned no structured_output");
        }
        return structured;
    }

    private String buildPrompt(JobHelpRequest r) {
        String description = r.jobDescription() == null ? "" : r.jobDescription().strip();
        if (description.length() > MAX_DESCRIPTION_CHARS) {
            description = description.substring(0, MAX_DESCRIPTION_CHARS);
        }
        String question = r.question() == null ? "" : r.question().strip();
        if (question.length() > MAX_QUESTION_CHARS) {
            question = question.substring(0, MAX_QUESTION_CHARS);
        }
        return """
                The candidate below is applying to a job and needs an answer to a question from the
                company's own application form (or a recruiter/referrer). Answer it ON THEIR BEHALF, in
                first person, ready to paste straight into the form.

                Company: %s
                Role: %s
                Job's stacks: %s
                Job's skill keywords: %s

                Job description:
                %s

                Candidate's real background (the ONLY source of truth for what to claim about them - never
                invent experience, employers, numbers, or skills beyond what's written here):
                %s

                The question to answer:
                %s

                Rules:
                - Every factual claim about the candidate must come from the background text above.
                - Answer only what was asked - no headers, no "Dear hiring manager", no signature.
                - Plain, professional, first-person tone, concise (a short paragraph unless the question
                  clearly calls for a list or a one-line answer).
                - If the question asks for something not answerable from the background (e.g. salary
                  expectation, availability date), give a brief, sensible placeholder answer and say so.
                """.formatted(
                r.company(), r.role(),
                r.stacks() == null ? "" : String.join(", ", r.stacks()),
                r.skills() == null ? "" : String.join(", ", r.skills()),
                description.isBlank() ? "(no description given)" : description,
                r.candidateProfile(),
                question.isBlank() ? "(no question given)" : question);
    }

    /** Built with Jackson's tree API - see ClaudeCliJdExtractor.buildSchema() for why not a raw string. */
    private String buildSchema() {
        ObjectNode root = JSON.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.putObject("answer").put("type", "string");
        root.putArray("required").add("answer");
        return JSON.writeValueAsString(root);
    }

    private static String winArg(String value) {
        return value.replace("\"", "\\\"");
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

    private static final java.io.File NUL_DEVICE =
            new java.io.File(System.getProperty("os.name", "").toLowerCase().contains("win") ? "NUL" : "/dev/null");
}
