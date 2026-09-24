package org.example.service;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Appends one line per Claude call - feature, cost and token counts, exactly as {@code claude -p} reported
 * them - to {@code data/ai-usage.jsonl}. Never shown in the UI; it exists so real per-feature cost can be
 * totalled later instead of guessed. A failure to write must never break the call that was being measured.
 */
@Slf4j
public final class UsageLog {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Path FILE = Path.of("data", "ai-usage.jsonl");

    private UsageLog() {
    }

    public static synchronized void record(String feature, JsonNode reply) {
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
