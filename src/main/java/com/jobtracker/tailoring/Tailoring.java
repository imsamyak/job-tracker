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

    /** One job of the user's experience and its bullets, one per line as written. */
    public record BulletSource(String id, List<String> bullets) {}

    /** A bullet that made it onto the page, exactly as written; {@code named} are the skills the job names that it shows. */
    public record Bullet(String text, List<String> named) {}

    /** The bullets chosen for one entry, best first. */
    public record EntryBullets(String id, List<Bullet> bullets) {}

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

    /**
     * Chooses and orders the user's existing bullets for one job, without rewriting any of them. A bullet ranks by the skills of
     * the job it names, then the skills the job's resume implies, then how many skills it shows at all, then how early it was
     * written. Bullets are taken best-first across the entries until the page lines run out.
     *
     * @param extraKeywords skills already known about the job from elsewhere (may be null)
     * @param maxLines      lines of the page the bullets may fill; a bullet is about {@value BulletPicker#CHARS_PER_LINE} characters per line
     */
    public List<EntryBullets> pickBullets(String jobDescription, Collection<String> extraKeywords, List<BulletSource> entries, int maxLines) {
        Tailored t = tailor(jobDescription, extraKeywords);
        Set<String> wanted = new java.util.LinkedHashSet<>(selector.namedIn(jobDescription));
        if (extraKeywords != null) {
            wanted.addAll(selector.namedIn(String.join("\n", extraKeywords)));
        }
        Set<String> shown = new java.util.HashSet<>();
        t.skills().sections().forEach(s -> shown.addAll(s.skills()));
        shown.removeAll(wanted);
        List<BulletPicker.Source> sources = entries.stream().map(e -> new BulletPicker.Source(e.id(), e.bullets() == null ? List.of() : e.bullets())).toList();
        return new BulletPicker(selector::namedIn).pick(wanted, shown, sources, maxLines).stream()
                .map(r -> new EntryBullets(r.id(), r.bullets().stream().map(b -> new Bullet(b.text(), b.named())).toList()))
                .toList();
    }

    /**
     * Which of the profile's summaries suits a job: {@code backend}, {@code frontend}, {@code full-stack} or {@code cloud}.
     * A job that stresses both a frontend and a backend stack is full-stack; none is {@code null} (use the general summary).
     */
    public static String summaryVariant(List<String> stacks) {
        if (stacks == null || stacks.isEmpty()) return null;
        boolean front = stacks.stream().anyMatch(s -> s.equals("react") || s.equals("vue"));
        boolean back = stacks.stream().anyMatch(s -> s.equals("java") || s.equals("node"));
        if (stacks.get(0).equals("cloud") && !front) return "cloud";
        if (front && back) return "full-stack";
        if (front) return "frontend";
        if (back) return "backend";
        return "cloud";
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
