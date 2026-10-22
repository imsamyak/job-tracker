package com.jobtracker.tailoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Chooses and orders the user's own experience bullets for one job. Nothing is rewritten: a bullet is either shown as it
 * was written or left out. "Which skills a text names" always comes from the skills file ({@code namedIn}).
 */
class BulletPicker {

    /** How many characters of a bullet fit on one line of the resume page. */
    static final int CHARS_PER_LINE = 100;

    record Source(String id, List<String> bullets) {}

    record Picked(String text, List<String> named) {}

    record Result(String id, List<Picked> bullets) {}

    private record Scored(int entry, int index, String text, List<String> named, int implied, int hits) {}

    private final Function<String, List<String>> namedIn;

    BulletPicker(Function<String, List<String>> namedIn) {
        this.namedIn = namedIn;
    }

    /**
     * Within an entry the order is: more skills the job names, then more skills the job implies (they are on this job's resume
     * but the job does not say them), then more skills at all, then the earlier bullet. The best bullet of every entry is taken
     * first, then the best of what is left, until the page has no room for the next one.
     *
     * @param wanted  skills the job names
     * @param implied skills this job's resume shows that the job does not name
     */
    List<Result> pick(Set<String> wanted, Set<String> implied, List<Source> sources, int maxLines) {
        List<List<Scored>> byEntry = new ArrayList<>();
        for (int e = 0; e < sources.size(); e++) {
            List<Scored> list = new ArrayList<>();
            List<String> bullets = sources.get(e).bullets();
            for (int i = 0; i < bullets.size(); i++) {
                String text = bullets.get(i) == null ? "" : bullets.get(i).strip();
                if (text.isEmpty()) continue;
                List<String> skills = namedIn.apply(text);
                List<String> named = skills.stream().filter(wanted::contains).toList();
                int imp = (int) skills.stream().filter(s -> !wanted.contains(s) && implied.contains(s)).count();
                list.add(new Scored(e, i, text, named, imp, skills.size()));
            }
            list.sort(Comparator.comparingInt((Scored s) -> -s.named().size())
                    .thenComparingInt(s -> -s.implied())
                    .thenComparingInt(s -> -s.hits())
                    .thenComparingInt(Scored::index));
            byEntry.add(list);
        }

        Set<Scored> chosen = new LinkedHashSet<>();
        int used = 0;
        // round by round: each entry's best remaining bullet, entries in the order given
        for (int round = 0; ; round++) {
            boolean any = false;
            for (List<Scored> list : byEntry) {
                if (round >= list.size()) continue;
                any = true;
                Scored s = list.get(round);
                int lines = lines(s.text());
                if (used + lines <= maxLines) {
                    chosen.add(s);
                    used += lines;
                }
            }
            if (!any) break;
        }

        List<Result> out = new ArrayList<>();
        for (int e = 0; e < sources.size(); e++) {
            int entry = e;
            List<Picked> picked = byEntry.get(e).stream().filter(chosen::contains).map(s -> new Picked(s.text(), s.named())).toList();
            out.add(new Result(sources.get(entry).id(), picked));
        }
        return out;
    }

    static int lines(String text) {
        return Math.max(1, (text.length() + 2 + CHARS_PER_LINE - 1) / CHARS_PER_LINE);
    }
}
