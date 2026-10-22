package com.jobtracker.tailoring;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The tailoring module's front door, and the only part of it the rest of the app may use.
 *
 * <p>Everything behind it (the rules engine, the rules file {@code tailoring/Meta.json}) is package-private and has no
 * knowledge of jobs, profiles, the database or the web layer: it takes plain lists in and gives plain records back.
 * That keeps the tailoring logic in one place that can be tested on its own, changed without touching the app, and
 * later moved into its own Maven module as it is. A test ({@code TailoringBoundaryTest}) fails the build if the app
 * reaches behind this class, or if the module starts depending on the app.
 */
public final class Tailoring {

    /** One line of the resume, for example {@code Databases: MySQL, PostgreSQL}. */
    public record Section(String label, List<String> skills) {}

    /**
     * The skills of a tailored resume.
     *
     * @param sections  the lines of the resume, in the order they appear
     * @param unmatched keywords from the job that are not a skill on your list; they are never added to the resume
     */
    public record Skills(List<Section> sections, List<String> unmatched) {}

    /**
     * Everything tailoring found out about one job.
     *
     * @param stacks what kind of role it is, most stressed first; empty when that could not be told
     * @param skills the resume's skill lines for this job; no lines when there is no stack to go by
     */
    public record Tailored(List<String> stacks, Skills skills) {}

    private final SkillSelector selector;
    private final JdReader reader;

    private Tailoring(SkillSelector selector, JdReader reader) {
        this.selector = selector;
        this.reader = reader;
    }

    /** The module with the rules shipped inside it. */
    public static Tailoring load() {
        return new Tailoring(SkillSelector.load(), JdReader.load());
    }

    /** The module with rules from elsewhere (tests, or a different candidate's rules file). */
    public static Tailoring load(java.io.InputStream rulesJson) {
        return new Tailoring(new SkillSelector(rulesJson), JdReader.load());
    }

    /**
     * Which skills a resume for this job shows, and in what order and groups.
     *
     * @param stacks   what kind of role it is, the main one first (see {@link #stacks()})
     * @param keywords skills and tools the job description names, such as "Java", "Kafka" or "k8s"
     * @throws IllegalArgumentException when no stack is given or the main stack is not one the rules know
     */
    public Skills skillsFor(List<String> stacks, Collection<String> keywords) {
        SkillSelector.Selection s = selector.select(stacks, keywords);
        return new Skills(s.sections().stream().map(x -> new Section(x.label(), x.skills())).toList(), s.unmatched());
    }

    /**
     * Tailors for one job from its description, for free: the description is read with keyword rules (no AI) to find the
     * kind of role and the skills it names, and the skills your rules say belong with them are chosen from that.
     *
     * @param extraKeywords skills already known about the job from elsewhere, such as the ones an AI read out when the job was added
     */
    public Tailored tailor(String jobDescription, Collection<String> extraKeywords) {
        JdReader.Reading reading = reader.read(jobDescription);
        // what the job names is decided by your rules' own skill names and aliases; the keyword reader only supplies the extra, curated terms
        java.util.LinkedHashSet<String> keywords = new java.util.LinkedHashSet<>(selector.namedIn(jobDescription));
        keywords.addAll(reading.skills());
        if (extraKeywords != null) keywords.addAll(extraKeywords);
        if (reading.stacks().isEmpty()) return new Tailored(List.of(), new Skills(List.of(), List.copyOf(keywords)));
        return new Tailored(reading.stacks(), skillsFor(reading.stacks(), keywords));
    }

    /** The kinds of role the rules understand (react, vue, node, java, cloud). */
    public Set<String> stacks() {
        return selector.knownStacks();
    }

    /** Every skill the rules know, so a job reader can be told which keywords are worth picking out. */
    public List<String> knownSkills() {
        return selector.knownSkills();
    }
}
