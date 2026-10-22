package com.jobtracker.tailoring;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads a job description with keyword rules only, no AI: which kinds of role it is (react, vue, node, java, cloud),
 * most stressed first, and which skills it names. The keyword tables are in {@code tailoring/keywords.json}.
 */
class JdReader {

    record Reading(List<String> stacks, List<String> skills) {}

    private record Named(String name, List<String> terms) {}

    private final int stackMinHits;
    private final List<Named> stacks = new ArrayList<>();
    private final List<Named> skills = new ArrayList<>();

    JdReader(InputStream keywordsJson) {
        JsonNode root = JsonMapper.builder().build().readTree(keywordsJson);
        stackMinHits = root.path("stackMinHits").asInt(1);
        for (JsonNode n : root.path("stacks")) {
            List<String> terms = new ArrayList<>();
            n.path("keywords").forEach(t -> terms.add(t.asText()));
            stacks.add(new Named(n.path("name").asText(), terms));
        }
        for (JsonNode n : root.path("skills")) {
            String name = n.path("name").asText();
            List<String> terms = new ArrayList<>(List.of(name));
            n.path("aliases").forEach(t -> terms.add(t.asText()));
            skills.add(new Named(name, terms));
        }
    }

    static JdReader load() {
        try (InputStream in = JdReader.class.getResourceAsStream("/tailoring/keywords.json")) {
            if (in == null) throw new IllegalStateException("Missing classpath resource /tailoring/keywords.json");
            return new JdReader(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Reading read(String text) {
        String t = text == null ? "" : text;
        record Scored(String name, int hits) {}
        List<Scored> scored = new ArrayList<>();
        for (Named s : stacks) {
            int hits = hits(t, s.terms());
            if (hits >= stackMinHits) scored.add(new Scored(s.name(), hits));
        }
        scored.sort(Comparator.comparingInt(Scored::hits).reversed()); // stable: ties keep the file's order
        List<String> found = new ArrayList<>();
        for (Named s : skills) if (hits(t, s.terms()) > 0) found.add(s.name());
        return new Reading(scored.stream().map(Scored::name).toList(), found);
    }

    /** Whole words only, ignoring case: "java" is not found inside "javascript". */
    private static int hits(String text, List<String> terms) {
        int hits = 0;
        for (String term : terms) {
            var m = Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(term) + "(?![A-Za-z0-9])", Pattern.CASE_INSENSITIVE).matcher(text);
            while (m.find()) hits++;
        }
        return hits;
    }
}
