package org.example.service.rules;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The editable keyword tables behind {@link RuleExtractor} (see {@code resources/pilot/keywords.json}).
 * Loaded fresh each time so tuning the file and re-running a backtest needs no restart when an override
 * file path is configured.
 */
public final class KeywordRules {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CLASSPATH = "/pilot/keywords.json";

    public record Named(String name, List<String> terms) {
    }

    public final int stackMinHits;
    public final List<Named> stacks;
    public final List<Named> modes;
    public final List<Named> types;
    /** name = canonical skill; terms = the name plus its aliases. */
    public final List<Named> skills;

    private KeywordRules(JsonNode root) {
        this.stackMinHits = root.path("stackMinHits").asInt(2);
        this.stacks = named(root.path("stacks"), "name", "keywords", false);
        this.modes = named(root.path("mode"), "value", "keywords", false);
        this.types = named(root.path("type"), "value", "keywords", false);
        this.skills = named(root.path("skills"), "name", "aliases", true);
    }

    /** @param overrideFile optional external file to use instead of the bundled one; ignored if absent */
    public static KeywordRules load(Path overrideFile) {
        try {
            if (overrideFile != null && Files.isRegularFile(overrideFile)) {
                return new KeywordRules(JSON.readTree(Files.readString(overrideFile)));
            }
            try (InputStream in = KeywordRules.class.getResourceAsStream(CLASSPATH)) {
                if (in == null) {
                    throw new IllegalStateException("Missing classpath resource " + CLASSPATH);
                }
                return new KeywordRules(JSON.readTree(in));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Named> named(JsonNode array, String nameField, String termsField, boolean nameIsATerm) {
        List<Named> out = new ArrayList<>();
        for (JsonNode n : array) {
            String name = n.path(nameField).asText();
            List<String> terms = new ArrayList<>();
            if (nameIsATerm) {
                terms.add(name);
            }
            n.path(termsField).forEach(t -> terms.add(t.asText()));
            out.add(new Named(name, terms));
        }
        return out;
    }

    /** Whole-word, case-insensitive: "java" must not match inside "javascript", "s3" not inside "s3cure". */
    public static Pattern wordPattern(String term) {
        return Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(term) + "(?![A-Za-z0-9])", Pattern.CASE_INSENSITIVE);
    }

    public static int countHits(String text, List<String> terms) {
        int hits = 0;
        for (String term : terms) {
            var m = wordPattern(term).matcher(text);
            while (m.find()) {
                hits++;
            }
        }
        return hits;
    }
}
