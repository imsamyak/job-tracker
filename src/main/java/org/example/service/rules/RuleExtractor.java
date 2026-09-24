package org.example.service.rules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls the job fields out of a posting's plain text with keyword rules only - no AI. Part of the pilot
 * that measures how close this gets to what Claude extracts (see {@link ExtractionPilotService}).
 * Company is deliberately not attempted: it has no reliable keyword signature in the text.
 */
public final class RuleExtractor {

    public record Result(String role, String jobId, String type, String mode, String location,
                         int experienceMin, Integer experienceMax, List<String> stacks, List<String> skills) {
    }

    private static final Pattern RANGE = Pattern.compile(
            "(\\d{1,2})\\s*(?:-|\\u2013|\\u2014|to)\\s*(\\d{1,2})\\s*\\+?\\s*(?:years?|yrs?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SINGLE = Pattern.compile("(\\d{1,2})\\s*\\+?\\s*(?:years?|yrs?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LABELLED_ID = Pattern.compile(
            "(?i)\\b(?:job|req(?:uisition)?|posting|position|reference)\\s*(?:id|no\\.?|number|code|#)\\s*[:#\\-]?\\s*([A-Za-z0-9][A-Za-z0-9_\\-/]+)");
    private static final Pattern PREFIXED_ID = Pattern.compile("\\b([A-Z]{1,3}-\\d{4,})\\b");
    private static final Pattern LOCATION = Pattern.compile("(?im)^\\s*(?:job\\s+)?locations?\\s*[:\\-]\\s*(.+)$");

    private final KeywordRules rules;

    public RuleExtractor(KeywordRules rules) {
        this.rules = rules;
    }

    public Result extract(String text) {
        String t = text == null ? "" : text;
        int[] exp = experience(t);
        return new Result(role(t), jobId(t), type(t), mode(t), location(t), exp[0], exp[1] < 0 ? null : exp[1],
                stacks(t), skills(t));
    }

    private List<String> stacks(String text) {
        record Scored(String name, int hits) {
        }
        List<Scored> scored = new ArrayList<>();
        for (var stack : rules.stacks) {
            int hits = KeywordRules.countHits(text, stack.terms());
            if (hits >= rules.stackMinHits) {
                scored.add(new Scored(stack.name(), hits));
            }
        }
        scored.sort(Comparator.comparingInt(Scored::hits).reversed());
        return scored.stream().map(Scored::name).toList();
    }

    private List<String> skills(String text) {
        List<String> found = new ArrayList<>();
        for (var skill : rules.skills) {
            if (KeywordRules.countHits(text, skill.terms()) > 0) {
                found.add(skill.name());
            }
        }
        return found;
    }

    private String mode(String text) {
        int hybrid = hits(text, rules.modes, "Hybrid");
        int remote = hits(text, rules.modes, "Remote");
        int onsite = hits(text, rules.modes, "Onsite");
        if (hybrid > 0) {
            return "Hybrid";
        }
        if (remote > 0 && onsite == 0) {
            return "Remote";
        }
        if (onsite > 0) {
            return "Onsite";
        }
        return remote > 0 ? "Remote" : "Onsite";
    }

    private String type(String text) {
        if (hits(text, rules.types, "Internship") > 0) {
            return "Internship";
        }
        if (hits(text, rules.types, "Contract") > 0) {
            return "Contract";
        }
        return "FullTime";
    }

    private static int hits(String text, List<KeywordRules.Named> table, String name) {
        return table.stream().filter(n -> n.name().equalsIgnoreCase(name))
                .mapToInt(n -> KeywordRules.countHits(text, n.terms())).sum();
    }

    /** {min, max} - max is -1 when the posting gives no upper bound. Prefers numbers near the word "experience". */
    private static int[] experience(String text) {
        int[] best = nearExperience(text, RANGE, true);
        if (best != null) {
            return best;
        }
        best = nearExperience(text, SINGLE, false);
        return best != null ? best : new int[]{0, -1};
    }

    private static int[] nearExperience(String text, Pattern pattern, boolean range) {
        Matcher m = pattern.matcher(text);
        int[] firstAny = null;
        while (m.find()) {
            int min = Integer.parseInt(m.group(1));
            int[] value = range ? new int[]{min, Integer.parseInt(m.group(2))} : new int[]{min, -1};
            String window = text.substring(Math.max(0, m.start() - 80), Math.min(text.length(), m.end() + 80));
            if (window.toLowerCase().contains("experience")) {
                return value;
            }
            if (firstAny == null) {
                firstAny = value;
            }
        }
        return null; // only a match near "experience" counts - anything else ("for 2 years of funding") is noise
    }

    private static String jobId(String text) {
        Matcher m = LABELLED_ID.matcher(text);
        while (m.find()) {
            if (m.group(1).chars().anyMatch(Character::isDigit)) {
                return m.group(1);
            }
        }
        Matcher p = PREFIXED_ID.matcher(text);
        return p.find() ? p.group(1) : null;
    }

    private static String location(String text) {
        Matcher m = LOCATION.matcher(text);
        return m.find() ? m.group(1).strip() : null;
    }

    private static String role(String text) {
        for (String line : text.split("\\R")) {
            String s = line.strip();
            if (!s.isEmpty()) {
                return s.length() <= 120 ? s : null;
            }
        }
        return null;
    }
}
