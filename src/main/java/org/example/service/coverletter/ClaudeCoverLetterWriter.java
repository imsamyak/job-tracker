package org.example.service.coverletter;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
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
 * Writes a tailored cover letter with the Claude Code CLI ({@code claude -p}) - same billing model (the
 * user's own Pro subscription) and the same CLI-invocation approach as {@code ClaudeCliJdExtractor}. No
 * file access is needed here (unlike extraction, which reads a PDF), so this runs with no tools at all.
 */
@Slf4j
public class ClaudeCoverLetterWriter implements CoverLetterWriter {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_STREAM_BYTES = 2_000_000;
    private static final int MAX_DESCRIPTION_CHARS = 6000;

    private final Path executable;
    private final String model;
    private final double maxBudgetUsd;
    private final Duration timeout;
    private final ExecutorService streamReaders = Executors.newCachedThreadPool();

    public ClaudeCoverLetterWriter(Path executable, String model, double maxBudgetUsd, Duration timeout) {
        this.executable = executable;
        this.model = model;
        this.maxBudgetUsd = maxBudgetUsd;
        this.timeout = timeout;
    }

    @Override
    public List<String> write(CoverLetterRequest request) {
        String prompt = buildPrompt(request);
        String schema = buildSchema();

        // Same Windows ProcessBuilder quoting fix as ClaudeCliJdExtractor - see that class for why.
        List<String> command = List.of(
                executable.toString(), "-p", winArg(prompt),
                "--output-format", "json",
                "--json-schema", winArg(schema),
                "--model", model,
                "--max-budget-usd", String.valueOf(maxBudgetUsd),
                // No --tools flag at all (unlike extraction's "Read") - this call needs no file/tool
                // access whatsoever, just text generation, and --permission-prompts none below means any
                // tool use it somehow attempted would simply fail closed rather than prompt.
                "--permission-prompts", "none",
                "--strict-mcp-config",
                "--no-session-persistence");

        JsonNode result = runClaude(command);
        List<String> paragraphs = strings(result, "paragraphs");
        if (paragraphs.isEmpty()) {
            throw new CoverLetterWriteException("claude returned no paragraphs");
        }
        return paragraphs;
    }

    private JsonNode runClaude(List<String> command) {
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .redirectInput(ProcessBuilder.Redirect.from(NUL_DEVICE))
                    .start();
        } catch (IOException e) {
            throw new CoverLetterWriteException("Could not start claude: " + e.getMessage(), e);
        }

        Future<byte[]> stdout = streamReaders.submit(() -> readLimited(process.getInputStream()));
        Future<byte[]> stderr = streamReaders.submit(() -> readLimited(process.getErrorStream()));

        boolean finished;
        try {
            finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new CoverLetterWriteException("Interrupted while waiting for claude", e);
        }
        if (!finished) {
            process.destroyForcibly();
            throw new CoverLetterWriteException("claude did not finish within " + timeout);
        }

        String out = collect(stdout);
        if (process.exitValue() != 0) {
            log.warn("claude exited {} - stderr: {}", process.exitValue(), collect(stderr));
            throw new CoverLetterWriteException("claude exited with code " + process.exitValue());
        }

        JsonNode reply;
        try {
            reply = JSON.readTree(out);
        } catch (Exception e) {
            throw new CoverLetterWriteException("claude did not return valid JSON: " + e.getMessage(), e);
        }
        org.example.service.UsageLog.record("cover-letter", reply);

        if (reply.path("is_error").asBoolean(false)) {
            List<String> errors = new ArrayList<>();
            reply.path("errors").forEach(n -> errors.add(n.asText()));
            throw new CoverLetterWriteException("claude reported an error: " + errors);
        }
        JsonNode structured = reply.get("structured_output");
        if (structured == null || structured.isNull()) {
            throw new CoverLetterWriteException("claude returned no structured_output");
        }
        return structured;
    }

    private static List<String> strings(JsonNode j, String field) {
        List<String> out = new ArrayList<>();
        j.path(field).forEach(n -> out.add(n.asText()));
        return out;
    }

    private String buildPrompt(CoverLetterRequest r) {
        String description = r.jobDescription() == null ? "" : r.jobDescription().strip();
        if (description.length() > MAX_DESCRIPTION_CHARS) {
            description = description.substring(0, MAX_DESCRIPTION_CHARS);
        }
        return """
                Write a tailored cover letter body for this candidate applying to the job below.

                Company: %s
                Role: %s
                Job's stacks: %s
                Job's skill keywords: %s

                Job description:
                %s

                Candidate's real background (the ONLY source of truth for what to claim about them - never
                invent experience, employers, numbers, or skills beyond what's written here):
                %s

                Write 3 short paragraphs (a few sentences each, no headers, no salutation, no signature -
                just the body):
                1. Opening: why this specific role/company is a good fit, referencing something concrete
                   from the job description.
                2. Middle: 1-2 pieces of the candidate's REAL experience above that most directly match
                   what this job asks for - be specific (technologies, what was built, the impact), not
                   generic.
                3. Closing: brief, confident close and interest in discussing further.

                Rules:
                - Every factual claim about the candidate must come from the background text above.
                - Do not restate the whole background - pick only what's relevant to THIS job.
                - Plain, professional, first-person tone. No clichés like "I am writing to express my
                  interest" or "team player" filler.
                """.formatted(
                r.company(), r.role(),
                r.stacks() == null ? "" : String.join(", ", r.stacks()),
                r.skills() == null ? "" : String.join(", ", r.skills()),
                description.isBlank() ? "(no description given)" : description,
                r.candidateProfile());
    }

    /** Built with Jackson's tree API - see ClaudeCliJdExtractor.buildSchema() for why not a raw string. */
    private String buildSchema() {
        ObjectNode root = JSON.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        ObjectNode paragraphs = props.putObject("paragraphs");
        paragraphs.put("type", "array");
        paragraphs.put("minItems", 3);
        paragraphs.put("maxItems", 4);
        paragraphs.putObject("items").put("type", "string");
        root.putArray("required").add("paragraphs");
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
