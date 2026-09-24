package org.example.service.intake;

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
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.util.concurrent.TimeUnit;

/**
 * Reads a job posting with the Claude Code CLI ({@code claude -p}), running on the user's own
 * subscription rather than a metered API key. Runs with only the Read tool and no project settings,
 * hooks or CLAUDE.md, so a hostile posting can at most make the extraction fail or return odd JSON -
 * never run a command or touch a file outside the posting's own temp folder.
 */
@Slf4j
public class ClaudeCliJdExtractor implements JdExtractor {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_STREAM_BYTES = 2_000_000;

    private final Path executable;
    private final String model;
    private final double maxBudgetUsd;
    private final Duration timeout;
    private final int maxPageTextChars;
    private final Set<String> knownStacks;
    /** true = pull the text out of the PDF locally and send Claude only that text (no Read tool, far
     *  fewer tokens); false = let Claude read the PDF itself. Set by jtraker.intake.mode=text|pdf. */
    private final boolean textMode;
    private final ExecutorService streamReaders = Executors.newCachedThreadPool();

    /** A PDF that yields less text than this (scanned/image-only, or extraction failed) is not trusted -
     *  that one posting falls back to letting Claude read the PDF. */
    static final int MIN_TEXT_CHARS = 300;

    public ClaudeCliJdExtractor(Path executable, String model, double maxBudgetUsd, Duration timeout,
                                 int maxPageTextChars, Set<String> knownStacks) {
        this(executable, model, maxBudgetUsd, timeout, maxPageTextChars, knownStacks, false);
    }

    public ClaudeCliJdExtractor(Path executable, String model, double maxBudgetUsd, Duration timeout,
                                 int maxPageTextChars, Set<String> knownStacks, boolean textMode) {
        this.executable = executable;
        this.model = model;
        this.maxBudgetUsd = maxBudgetUsd;
        this.timeout = timeout;
        this.maxPageTextChars = maxPageTextChars;
        this.knownStacks = knownStacks;
        this.textMode = textMode;
    }

    @Override
    public ExtractedJob extract(Path jdPdf, String pageText) {
        String schema = buildSchema();
        // The description is never asked of Claude (re-writing it was ~85% of the output cost): it is the
        // posting's own text, straight from the PDF, and the full PDF stays one click away in the app.
        String postingText = pdfText(jdPdf);
        String description = descriptionOf(postingText);

        if (textMode) {
            if (postingText != null && postingText.length() >= MIN_TEXT_CHARS) {
                String prompt = buildTextPrompt(postingText);
                // Same flags as the cover letter writer: pure text in, JSON out - no tools, no file access.
                List<String> command = List.of(
                        executable.toString(), "-p", winArg(prompt),
                        "--output-format", "json",
                        "--json-schema", winArg(schema),
                        "--model", model,
                        "--max-budget-usd", String.valueOf(maxBudgetUsd),
                        "--permission-prompts", "none",
                        "--strict-mcp-config",
                        "--no-session-persistence");
                return toExtractedJob(runClaude(command, "intake-text"), description);
            }
            log.info("Intake text mode: only {} chars of text in {} - falling back to letting Claude read the PDF",
                    postingText == null ? 0 : postingText.length(), jdPdf.getFileName());
        }

        String prompt = buildPrompt(jdPdf, pageText);

        // Java's ProcessBuilder on Windows passes an argument through unquoted whenever it contains no
        // space, with no escaping of embedded '"' characters. The prompt and schema below are full of
        // them, so left as-is the child process's own argv parser (which treats a bare '"' as a toggle
        // for quoted mode) silently strips every one - e.g. a JSON schema arrives with every quote gone.
        // Escaping '"' as \" here survives that parser correctly; backslashes must be left untouched,
        // since Windows only treats them specially when they immediately precede a quote.
        List<String> command = List.of(
                executable.toString(), "-p", winArg(prompt),
                "--output-format", "json",
                "--json-schema", winArg(schema),
                "--model", model,
                "--max-budget-usd", String.valueOf(maxBudgetUsd),
                "--tools", "Read",
                "--permission-prompts", "none",
                "--strict-mcp-config",
                "--no-session-persistence",
                "--add-dir", winArg(jdPdf.toAbsolutePath().getParent().toString()));

        JsonNode result = runClaude(command, "intake");
        return toExtractedJob(result, description);
    }

    private static final int MAX_DESCRIPTION_CHARS = 30_000;

    private static String descriptionOf(String pdfText) {
        if (pdfText == null || pdfText.isBlank()) {
            return "(The posting's text could not be read from its PDF - open the PDF for the details.)";
        }
        return pdfText.length() > MAX_DESCRIPTION_CHARS ? pdfText.substring(0, MAX_DESCRIPTION_CHARS) : pdfText;
    }

    /** The posting's text pulled straight out of its PDF, or null when that fails. Package-private for tests. */
    public static String pdfText(Path pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            return new PDFTextStripper().getText(doc).strip();
        } catch (IOException | RuntimeException e) {
            log.warn("Could not extract text from {}: {}", pdf.getFileName(), e.getMessage());
            return null;
        }
    }

    private JsonNode runClaude(List<String> command, String usageFeature) {
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(command)
                    .redirectInput(ProcessBuilder.Redirect.from(NUL_DEVICE));
            // Extraction is mechanical - no hidden "thinking" tokens needed (they were thousands of output
            // tokens per call, the bulk of the output cost, for a JSON answer of a few hundred).
            builder.environment().put("MAX_THINKING_TOKENS", "0");
            process = builder.start();
        } catch (IOException e) {
            throw new JdExtractionException("Could not start claude: " + e.getMessage(), e);
        }

        Future<byte[]> stdout = streamReaders.submit(() -> readLimited(process.getInputStream()));
        Future<byte[]> stderr = streamReaders.submit(() -> readLimited(process.getErrorStream()));

        boolean finished;
        try {
            finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new JdExtractionException("Interrupted while waiting for claude", e);
        }
        if (!finished) {
            process.destroyForcibly();
            throw new JdExtractionException("claude did not finish within " + timeout);
        }

        String out = collect(stdout);
        if (process.exitValue() != 0) {
            log.warn("claude exited {} - stderr: {}", process.exitValue(), collect(stderr));
            throw new JdExtractionException("claude exited with code " + process.exitValue());
        }

        JsonNode reply;
        try {
            reply = JSON.readTree(out);
        } catch (Exception e) {
            throw new JdExtractionException("claude did not return valid JSON: " + e.getMessage(), e);
        }
        org.example.service.UsageLog.record(usageFeature, reply);

        if (reply.path("is_error").asBoolean(false)) {
            List<String> errors = new ArrayList<>();
            reply.path("errors").forEach(n -> errors.add(n.asText()));
            throw new JdExtractionException("claude reported an error: " + errors);
        }
        JsonNode structured = reply.get("structured_output");
        if (structured == null || structured.isNull()) {
            throw new JdExtractionException("claude returned no structured_output");
        }
        return structured;
    }

    private ExtractedJob toExtractedJob(JsonNode j, String description) {
        return new ExtractedJob(
                text(j, "company"),
                text(j, "role"),
                j.hasNonNull("jobId") ? j.get("jobId").asText() : null,
                text(j, "type"),
                text(j, "mode"),
                text(j, "location"),
                j.path("experienceMinYears").asInt(0),
                j.hasNonNull("experienceMaxYears") ? j.get("experienceMaxYears").asInt() : null,
                strings(j, "stacks"),
                strings(j, "skills"),
                strings(j, "contacts"),
                description);
    }

    private static String text(JsonNode j, String field) {
        return j.hasNonNull(field) ? j.get(field).asText() : null;
    }

    private static List<String> strings(JsonNode j, String field) {
        List<String> out = new ArrayList<>();
        j.path(field).forEach(n -> out.add(n.asText()));
        return out;
    }

    /** Text mode: the posting's text (already pulled out of the PDF) is the only source. */
    private String buildTextPrompt(String postingText) {
        String text = postingText.length() > maxPageTextChars ? postingText.substring(0, maxPageTextChars) : postingText;
        return """
                You are given the plain text of one job posting (extracted from the page the applicant saw;
                page furniture such as navigation, ads and footers may be mixed in).

                """ + EXTRACTION_RULES + "\n\nPosting text:\n" + text + "\n";
    }

    /** Shared by both modes so they can never drift apart. */
    private static final String EXTRACTION_RULES = """
                Extract the fields defined by the JSON schema you were given. Rules:
                - stacks: pick from react, vue, node, java, cloud only; list every stack the ROLE itself
                  requires, most emphasized first. A job that is frontend-only should list only react and/or
                  vue; do not add a backend stack the posting does not ask for.
                - skills: list the technical skill keywords the posting asks for, using the posting's own
                  wording (e.g. "Kafka", "k8s", "Spring Boot"). Do not invent skills the posting does not
                  mention.
                - type and mode: pick the single best match; if the posting truly does not say, make the
                  most reasonable choice from the allowed values rather than guessing wildly.
                - location: the city/region the posting names for this role (e.g. "Bengaluru, India" or
                  "Austin, TX"). Omit it when the posting genuinely gives no location.
                - experienceMinYears / experienceMaxYears: numeric years from the posting; 0 when it does
                  not state a minimum. Omit experienceMaxYears when no upper bound is given.
                - Never fabricate a company name, role title or any other field. If something is genuinely
                  not in the posting, use an empty string, empty list, or omit the optional field.""";

    private String buildPrompt(Path jdPdf, String pageText) {
        String text = pageText == null ? "" : pageText.strip();
        if (text.length() > maxPageTextChars) {
            text = text.substring(0, maxPageTextChars);
        }
        return """
                You are given one job posting: a PDF of the page the applicant saw, at the path below, and
                the page's plain text as supporting context (the PDF is the primary source of truth; use the
                text only to resolve wording the PDF's layout makes unclear).

                PDF path: """ + jdPdf.toAbsolutePath() + "\n\n" + EXTRACTION_RULES
                + "\n\nPage text:\n" + (text.isBlank() ? "(none given)" : text) + "\n";
    }

    /**
     * Built with Jackson's tree API, not hand-written JSON text: a schema passed as a single command-line
     * argument has no room for a stray character, and manual string formatting had already produced one.
     */
    private String buildSchema() {
        ObjectNode root = JSON.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");

        props.putObject("company").put("type", "string");
        props.putObject("role").put("type", "string");
        props.putObject("jobId").put("type", "string");
        enumProperty(props, "type", "string", List.of("Internship", "FullTime", "Contract"));
        enumProperty(props, "mode", "string", List.of("Hybrid", "Remote", "Onsite"));
        props.putObject("location").put("type", "string");
        props.putObject("experienceMinYears").put("type", "integer").put("minimum", 0);
        props.putObject("experienceMaxYears").put("type", "integer").put("minimum", 0);

        ObjectNode stacks = props.putObject("stacks");
        stacks.put("type", "array");
        stacks.put("minItems", 1);
        enumProperty2(stacks.putObject("items"), "string", knownStacks);

        props.putObject("skills").put("type", "array").putObject("items").put("type", "string");
        props.putObject("contacts").put("type", "array").putObject("items").put("type", "string");
        ArrayNode required = root.putArray("required");
        List.of("company", "role", "type", "mode", "experienceMinYears", "stacks", "skills")
                .forEach(required::add);

        return JSON.writeValueAsString(root);
    }

    private static void enumProperty(ObjectNode props, String name, String type, List<String> values) {
        ObjectNode prop = props.putObject(name);
        prop.put("type", type);
        ArrayNode e = prop.putArray("enum");
        values.forEach(e::add);
    }

    private static void enumProperty2(ObjectNode prop, String type, Collection<String> values) {
        prop.put("type", type);
        ArrayNode e = prop.putArray("enum");
        values.forEach(e::add);
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
