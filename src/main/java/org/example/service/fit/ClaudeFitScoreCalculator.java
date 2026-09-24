package org.example.service.fit;

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
 * Judges fit with the Claude Code CLI ({@code claude -p}) - same billing model and CLI-invocation approach
 * as {@code ClaudeCliJdExtractor}/{@code ClaudeCoverLetterWriter}. No file/tool access needed, same as the
 * cover letter writer.
 */
@Slf4j
public class ClaudeFitScoreCalculator implements FitScoreCalculator {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_STREAM_BYTES = 2_000_000;
    private static final int MAX_DESCRIPTION_CHARS = 6000;

    private final Path executable;
    private final String model;
    private final double maxBudgetUsd;
    private final Duration timeout;
    private final ExecutorService streamReaders = Executors.newCachedThreadPool();

    public ClaudeFitScoreCalculator(Path executable, String model, double maxBudgetUsd, Duration timeout) {
        this.executable = executable;
        this.model = model;
        this.maxBudgetUsd = maxBudgetUsd;
        this.timeout = timeout;
    }

    @Override
    public FitScoreResult calculate(FitScoreRequest request) {
        String prompt = buildPrompt(request);
        String schema = buildSchema();

        // Same Windows ProcessBuilder quoting fix as ClaudeCliJdExtractor - see that class for why.
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
        if (!result.hasNonNull("percentage")) {
            throw new FitScoreCalculationException("claude returned no percentage");
        }
        return new FitScoreResult(result.get("percentage").asInt(),
                result.hasNonNull("summary") ? result.get("summary").asText() : "");
    }

    private JsonNode runClaude(List<String> command) {
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .redirectInput(ProcessBuilder.Redirect.from(NUL_DEVICE))
                    .start();
        } catch (IOException e) {
            throw new FitScoreCalculationException("Could not start claude: " + e.getMessage(), e);
        }

        Future<byte[]> stdout = streamReaders.submit(() -> readLimited(process.getInputStream()));
        Future<byte[]> stderr = streamReaders.submit(() -> readLimited(process.getErrorStream()));

        boolean finished;
        try {
            finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new FitScoreCalculationException("Interrupted while waiting for claude", e);
        }
        if (!finished) {
            process.destroyForcibly();
            throw new FitScoreCalculationException("claude did not finish within " + timeout);
        }

        String out = collect(stdout);
        if (process.exitValue() != 0) {
            log.warn("claude exited {} - stderr: {}", process.exitValue(), collect(stderr));
            throw new FitScoreCalculationException("claude exited with code " + process.exitValue());
        }

        JsonNode reply;
        try {
            reply = JSON.readTree(out);
        } catch (Exception e) {
            throw new FitScoreCalculationException("claude did not return valid JSON: " + e.getMessage(), e);
        }
        org.example.service.UsageLog.record("fit-score", reply);

        if (reply.path("is_error").asBoolean(false)) {
            List<String> errors = new ArrayList<>();
            reply.path("errors").forEach(n -> errors.add(n.asText()));
            throw new FitScoreCalculationException("claude reported an error: " + errors);
        }
        JsonNode structured = reply.get("structured_output");
        if (structured == null || structured.isNull()) {
            throw new FitScoreCalculationException("claude returned no structured_output");
        }
        return structured;
    }

    private String buildPrompt(FitScoreRequest r) {
        String description = r.jobDescription() == null ? "" : r.jobDescription().strip();
        if (description.length() > MAX_DESCRIPTION_CHARS) {
            description = description.substring(0, MAX_DESCRIPTION_CHARS);
        }
        String experience = r.experienceMaxYears() != null
                ? r.experienceMinYears() + "-" + r.experienceMaxYears() + " years"
                : r.experienceMinYears() + "+ years";

        return """
                Judge how well this candidate fits this job, as a percentage from 0 to 100.

                Company: %s
                Role: %s
                Job's stacks: %s
                Job's skill keywords: %s
                Job's experience requirement: %s

                Job description:
                %s

                Candidate's real background (the ONLY source of truth about them - never assume skills or
                experience beyond what's written here):
                %s

                Judge fit on: overlap between the candidate's real technical experience and what the job
                actually asks for, whether the candidate's years of experience roughly meets the job's
                requirement, and how directly the candidate's real project/work experience (not just
                keyword overlap) matches the core responsibilities described.

                Be honest and specific, not generous by default - a candidate with little real overlap
                should score low (well under 50), and only a genuinely strong, specific match should score
                above 80. Return the percentage and one short sentence explaining the main reason for it.
                """.formatted(
                r.company(), r.role(),
                r.stacks() == null ? "" : String.join(", ", r.stacks()),
                r.skills() == null ? "" : String.join(", ", r.skills()),
                experience,
                description.isBlank() ? "(no description given)" : description,
                r.candidateProfile());
    }

    /** Built with Jackson's tree API - see ClaudeCliJdExtractor.buildSchema() for why not a raw string. */
    private String buildSchema() {
        ObjectNode root = JSON.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.putObject("percentage").put("type", "integer").put("minimum", 0).put("maximum", 100);
        props.putObject("summary").put("type", "string");
        ArrayNode required = root.putArray("required");
        required.add("percentage");
        required.add("summary");
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
