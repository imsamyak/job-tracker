package org.example.service.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Scores the rule extractor's output field by field against what Claude extracted for the same posting.
 * "Matches Claude" is agreement, not proof of correctness - Claude can be wrong too - which is why every
 * comparison keeps both values and a detail string for reading the disagreements by eye.
 */
public final class ExtractionScorer {

    /** What Claude extracted (from a stored job or a fresh extraction) - the reference side of the comparison. */
    public record Facts(String role, String jobId, String type, String mode, String location,
                        int experienceMin, Integer experienceMax, List<String> stacks, List<String> skills) {
    }

    public record FieldScore(String field, String ruleValue, String claudeValue, double score, boolean matched,
                             String detail) {
    }

    private final Map<String, String> canonicalSkill = new HashMap<>();

    public ExtractionScorer(KeywordRules rules) {
        for (var skill : rules.skills) {
            for (String term : skill.terms()) {
                canonicalSkill.put(term.toLowerCase(Locale.ROOT), skill.name().toLowerCase(Locale.ROOT));
            }
        }
    }

    public List<FieldScore> score(RuleExtractor.Result rule, Facts claude) {
        List<FieldScore> out = new ArrayList<>();

        out.add(exact("type", rule.type(), claude.type()));
        out.add(exact("mode", rule.mode(), claude.mode()));
        out.add(numbers("experience_min", rule.experienceMin(), claude.experienceMin()));
        out.add(nullableNumbers("experience_max", rule.experienceMax(), claude.experienceMax()));
        out.add(lenientId("job_id", rule.jobId(), claude.jobId()));
        out.add(contains("location", rule.location(), claude.location(), true));
        out.add(contains("role", rule.role(), claude.role(), false));

        Set<String> ruleStacks = lower(rule.stacks());
        Set<String> claudeStacks = lower(claude.stacks());
        out.add(sets("stacks", ruleStacks, claudeStacks));
        String rulePrimary = rule.stacks().isEmpty() ? null : rule.stacks().get(0);
        String claudePrimary = claude.stacks() == null || claude.stacks().isEmpty() ? null : claude.stacks().get(0);
        out.add(exact("stacks_primary", rulePrimary, claudePrimary));

        // Skills: only the part of Claude's list the dictionary can express is a fair target - Claude also
        // lists skills nobody put in the keyword table yet, and those show up in "unknown" for reading, not as misses.
        Set<String> claudeKnown = new HashSet<>();
        List<String> claudeUnknown = new ArrayList<>();
        for (String s : claude.skills() == null ? List.<String>of() : claude.skills()) {
            String canonical = canonicalSkill.get(s.toLowerCase(Locale.ROOT).strip());
            if (canonical != null) {
                claudeKnown.add(canonical);
            } else {
                claudeUnknown.add(s);
            }
        }
        Set<String> ruleSkills = lower(rule.skills());
        FieldScore jaccard = sets("skills", ruleSkills, claudeKnown);
        out.add(new FieldScore("skills", jaccard.ruleValue(), jaccard.claudeValue(), jaccard.score(),
                jaccard.matched(), jaccard.detail() + " | not in dictionary: " + String.join(", ", claudeUnknown)));
        out.add(ratio("skills_recall", ruleSkills, claudeKnown, true));
        out.add(ratio("skills_precision", ruleSkills, claudeKnown, false));
        return out;
    }

    private static FieldScore exact(String field, String rule, String claude) {
        boolean same = Objects.equals(norm(rule), norm(claude));
        return new FieldScore(field, rule, claude, same ? 1 : 0, same, same ? "" : "rule=" + rule + " claude=" + claude);
    }

    private static FieldScore numbers(String field, int rule, int claude) {
        boolean same = rule == claude;
        return new FieldScore(field, String.valueOf(rule), String.valueOf(claude), same ? 1 : 0, same, "");
    }

    /** "No upper bound" is stored by the job entity as 0 but comes out of the rules as null - the same answer. */
    private static FieldScore nullableNumbers(String field, Integer rule, Integer claude) {
        boolean same = Objects.equals(rule == null ? 0 : rule, claude == null ? 0 : claude);
        return new FieldScore(field, String.valueOf(rule), String.valueOf(claude), same ? 1 : 0, same, "");
    }

    /** Both blank counts as a match (the posting has no id and neither side invented one). */
    private static FieldScore lenientId(String field, String rule, String claude) {
        boolean same = norm(rule).equals(norm(claude));
        return new FieldScore(field, rule, claude, same ? 1 : 0, same, same ? "" : "rule=" + rule + " claude=" + claude);
    }

    private static FieldScore contains(String field, String rule, String claude, boolean firstPartOnly) {
        String a = alnum(rule);
        String b = alnum(claude);
        boolean same;
        if (a.isEmpty() && b.isEmpty()) {
            same = true;
        } else if (a.isEmpty() || b.isEmpty()) {
            same = false;
        } else if (firstPartOnly) {
            String aFirst = alnum(rule.split(",")[0]);
            String bFirst = alnum(claude.split(",")[0]);
            same = a.contains(bFirst) || b.contains(aFirst);
        } else {
            same = a.contains(b) || b.contains(a);
        }
        return new FieldScore(field, rule, claude, same ? 1 : 0, same, same ? "" : "rule=" + rule + " claude=" + claude);
    }

    private static FieldScore sets(String field, Set<String> rule, Set<String> claude) {
        Set<String> inter = new HashSet<>(rule);
        inter.retainAll(claude);
        Set<String> union = new HashSet<>(rule);
        union.addAll(claude);
        double jaccard = union.isEmpty() ? 1.0 : (double) inter.size() / union.size();
        Set<String> missed = new HashSet<>(claude);
        missed.removeAll(rule);
        Set<String> extra = new HashSet<>(rule);
        extra.removeAll(claude);
        return new FieldScore(field, String.join(", ", sorted(rule)), String.join(", ", sorted(claude)), jaccard,
                missed.isEmpty() && extra.isEmpty(),
                "missed: " + String.join(", ", sorted(missed)) + " | extra: " + String.join(", ", sorted(extra)));
    }

    /** recall = share of Claude's known skills the rules also found; precision = share of the rules' finds Claude also listed. */
    private static FieldScore ratio(String field, Set<String> rule, Set<String> claude, boolean recall) {
        Set<String> inter = new HashSet<>(rule);
        inter.retainAll(claude);
        Set<String> denominator = recall ? claude : rule;
        double score = denominator.isEmpty() ? 1.0 : (double) inter.size() / denominator.size();
        return new FieldScore(field, String.join(", ", sorted(rule)), String.join(", ", sorted(claude)), score,
                score >= 0.999, "");
    }

    private static Set<String> lower(List<String> values) {
        Set<String> out = new HashSet<>();
        if (values != null) {
            values.forEach(v -> out.add(v.toLowerCase(Locale.ROOT).strip()));
        }
        return out;
    }

    private static List<String> sorted(Set<String> s) {
        return s.stream().sorted().toList();
    }

    private static String norm(String s) {
        return s == null ? "" : s.strip().toLowerCase(Locale.ROOT);
    }

    private static String alnum(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }
}
