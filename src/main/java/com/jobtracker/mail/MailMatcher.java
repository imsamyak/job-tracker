package com.jobtracker.mail;

import com.jobtracker.job.Job;
import com.jobtracker.job.JobStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The cheap first filter: which of your jobs could this email be about? Only emails that mention a company you
 * are tracking (or one of its job ids) are ever shown to Claude, so most of your mailbox is never sent anywhere.
 */
final class MailMatcher {

    /** First words too common to say anything about a company on their own. */
    private static final Set<String> GENERIC = Set.of(
            "global", "first", "digital", "smart", "tech", "data", "cloud", "software", "systems", "solutions",
            "technologies", "technology", "services", "india", "group", "new", "next", "the", "blue", "open", "info");

    private MailMatcher() {}

    /** Mail that is never an application: alerts, recommendations, digests. Skipped without asking Claude. */
    private static final java.util.regex.Pattern BULK = java.util.regex.Pattern.compile(
            "job alert|jobs? (for you|similar|recommended)|new jobs?|recommended for you|digest|newsletter|webinar|top picks|people also viewed|you appeared in");

    /** What a mail about your own application usually says. A cheap first filter: most of a mailbox is not one. */
    private static final java.util.regex.Pattern APPLICATION = java.util.regex.Pattern.compile(
            "your application|thank(s| you) for applying|applied (for|to)|application (was |has been )?(received|submitted|sent|status|update)|we received your|"
            + "interview|shortlist|assessment|coding (test|challenge|round)|not (be )?(moving|proceeding) forward|unfortunately|other candidates|regret to inform|"
            + "offer letter|next (steps|round)|selected for|we will not be");

    /** Could this email be about an application of yours, even for a company that is not tracked yet? */
    static boolean looksLikeApplication(MailMessage m) {
        String subject = m.subject() == null ? "" : m.subject().toLowerCase(Locale.ROOT);
        if (BULK.matcher(subject).find()) return false;
        String text = m.text() == null ? "" : m.text();
        String head = (subject + " " + text.substring(0, Math.min(text.length(), 2000))).toLowerCase(Locale.ROOT);
        return APPLICATION.matcher(head).find();
    }

    /** Jobs this message may be about. Jobs already rejected are left out: there is nothing more to learn about them. */
    static List<Job> candidates(MailMessage m, List<Job> jobs) {
        String hay = " " + normalize(m.from() + " " + m.subject() + " " + m.text()) + " ";
        List<Job> out = new ArrayList<>();
        for (Job j : jobs) {
            if (j.getStatus() == JobStatus.REJECTED || j.getCompany() == null) continue;
            if (mentions(hay, j)) out.add(j);
        }
        return out;
    }

    private static boolean mentions(String hay, Job j) {
        String full = normalize(j.getCompany());
        if (full.isEmpty()) return false;
        if (hay.contains(" " + full + " ")) return true;

        String[] words = full.split(" ");
        if (words.length > 1 && words[0].length() >= 4 && !GENERIC.contains(words[0]) && hay.contains(" " + words[0] + " ")) return true;

        String id = j.getPostingId() == null ? "" : normalize(j.getPostingId());
        return id.length() >= 4 && hay.contains(" " + id + " ");
    }

    /** Lower case, letters and digits only, single spaces: "Acme Systems, Inc." becomes "acme systems inc". */
    static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]+", " ").strip();
    }
}
