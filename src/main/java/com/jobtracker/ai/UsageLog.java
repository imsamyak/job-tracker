package com.jobtracker.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Appends one line per Claude call (feature, cost, token counts, as the CLI reported them) to
 * {@code data/ai-usage.jsonl}, so real per-feature cost can be totalled later instead of guessed.
 * A failure to write must never break the call being measured.
 */
final class UsageLog {
    private static final Logger log = LoggerFactory.getLogger(UsageLog.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Path FILE = Path.of("data", "ai-usage.jsonl");

    private UsageLog() {}

    static synchronized void record(String feature, JsonNode reply) {
        try {
            JsonNode usage = reply.path("usage");
            ObjectNode line = JSON.createObjectNode();
            line.put("ts", Instant.now().toString());
            line.put("feature", feature);
            line.put("usd", reply.path("total_cost_usd").asDouble(0));
            line.put("inputTokens", usage.path("input_tokens").asLong(0));
            line.put("outputTokens", usage.path("output_tokens").asLong(0));
            line.put("cacheReadTokens", usage.path("cache_read_input_tokens").asLong(0));
            line.put("cacheCreationTokens", usage.path("cache_creation_input_tokens").asLong(0));
            line.put("turns", reply.path("num_turns").asLong(0));
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, JSON.writeValueAsString(line) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not record AI usage for {}: {}", feature, e.getMessage());
        }
    }
}
