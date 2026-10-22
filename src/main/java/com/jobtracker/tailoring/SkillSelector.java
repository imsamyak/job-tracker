package com.jobtracker.tailoring;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;

/**
 * Picks and orders the skills of a resume from the rule-based {@code templates/resume/Meta.json}.
 * <p>
 * Input: the stacks the job asks for (first one is the primary stack) and the skill keywords already
 * extracted from the job description. Output: the ordered resume sections plus the keywords that match
 * no known skill. See {@code _readme} in Meta.json for the rules.
 */
class SkillSelector {

    private static final String DEFAULT_META = "/tailoring/Meta.json";

    /** One line of the resume, e.g. {@code Databases: MySQL, PostgreSQL}. */
    public record ResumeSection(String label, List<String> skills) {
    }

    /**
     * @param sections  ordered resume sections
     * @param unmatched keywords that are not a known skill name or alias (never added to the resume)
     */
    public record Selection(List<ResumeSection> sections, List<String> unmatched) {
    }

    /** Why a skill is in the resume. The rank decides the order inside a category. */
    private enum Reason {
        JD(0), CORE(1), RELATED(2), PULLED(2), DEP(3);

        final int rank;

        Reason(int rank) {
            this.rank = rank;
        }
    }

    private record Skill(
            String name,
            Set<String> keys,
            Object include,
            Reason baseReason,
            List<String> requires,
            List<String> pulls,
            Object exclude,
            List<Map<String, Object>> moveTo,
            String category,
            int indexInCategory
    ) {
    }

    private record Group(String label, List<String> sources) {
    }

    private final Map<String, Skill> skills = new LinkedHashMap<>();
    private final Map<String, String> skillNameByKey = new HashMap<>();
    private final List<Group> groups = new ArrayList<>();
    private final Map<String, List<String>> groupOrder = new LinkedHashMap<>();
    private final String promotedGroup;
    private final Set<String> promoteWhenStacks;

    public SkillSelector(InputStream metaJson) {
        Map<String, Object> root = asMap(read(metaJson));

        for (Object g : asList(root.get("outputGroups"))) {
            Map<String, Object> gm = asMap(g);
            groups.add(new Group((String) gm.get("label"), strings(gm.get("sources"))));
        }
        asMap(root.get("groupOrder")).forEach((stack, order) -> groupOrder.put(stack, strings(order)));

        Map<String, Object> promote = asMap(root.get("promoteGroup"));
        promotedGroup = (String) promote.get("group");
        promoteWhenStacks = new HashSet<>(strings(promote.get("whenStacksPicked")));

        asMap(root.get("skills")).forEach((category, list) -> {
            int index = 0;
            for (Object o : asList(list)) {
                Skill skill = toSkill(category, index++, asMap(o));
                if (skills.put(skill.name(), skill) != null) {
                    throw new IllegalStateException("Duplicate skill: " + skill.name());
                }
                for (String key : skill.keys()) {
                    String other = skillNameByKey.put(key, skill.name());
                    if (other != null && !other.equals(skill.name())) {
                        throw new IllegalStateException("Keyword '" + key + "' belongs to both " + other + " and " + skill.name());
                    }
                }
            }
        });

        validate();
    }

    /** Loads the Meta.json shipped in the application resources. */
    public static SkillSelector load() {
        try (InputStream in = SkillSelector.class.getResourceAsStream(DEFAULT_META)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + DEFAULT_META);
            }
            return new SkillSelector(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The skills a text names, using this selector's own skill names and aliases (whole words, ignoring case), in the order of
     * the skills file. One vocabulary decides what "named in the job" means, so it always agrees with {@link #select}.
     */
    public List<String> namedIn(String text) {
        List<String> found = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return found;
        }
        for (Skill s : skills.values()) {
            for (String key : s.keys()) {
                if (key.isEmpty()) {
                    continue;
                }
                if (java.util.regex.Pattern.compile("(?<![A-Za-z0-9])" + java.util.regex.Pattern.quote(key) + "(?![A-Za-z0-9])",
                        java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text).find()) {
                    found.add(s.name());
                    break;
                }
            }
        }
        return found;
    }

    /** Every skill name the selector knows, for telling the extractor which keywords are valid. */
    public List<String> knownSkills() {
        return List.copyOf(skills.keySet());
    }

    /** Stack ids the selector accepts as a primary stack (react, vue, node, java, cloud). */
    public Set<String> knownStacks() {
        return Set.copyOf(groupOrder.keySet());
    }

    /**
     * @param stacks   stacks picked for the job, primary stack first (react, vue, node, java, cloud)
     * @param keywords skill keywords from the job description, e.g. "Java", "Kafka", "k8s"
     */
    public Selection select(List<String> stacks, Collection<String> keywords) {
        if (stacks == null || stacks.isEmpty()) {
            throw new IllegalArgumentException("At least one stack is required");
        }
        List<String> order = groupOrder.get(stacks.get(0));
        if (order == null) {
            throw new IllegalArgumentException("Unknown primary stack: " + stacks.get(0));
        }

        Set<String> namedSkills = new HashSet<>();
        List<String> unmatched = new ArrayList<>();
        for (String keyword : keywords == null ? List.<String>of() : keywords) {
            String skill = skillNameByKey.get(normalize(keyword));
            if (skill != null) {
                namedSkills.add(skill);
            } else if (keyword != null && !keyword.isBlank()) {
                unmatched.add(keyword.trim());
            }
        }

        Run run = new Run(new HashSet<>(stacks), namedSkills);
        Map<String, Reason> included = includeSkills(run);
        Map<String, String> categoryOf = moveSkills(run, included);

        List<String> groupLabels = new ArrayList<>(order);
        if (stacks.stream().anyMatch(promoteWhenStacks::contains) && groupLabels.remove(promotedGroup)) {
            groupLabels.add(Math.min(1, groupLabels.size()), promotedGroup);
        }

        Map<String, Group> groupByLabel = new HashMap<>();
        groups.forEach(g -> groupByLabel.put(g.label(), g));

        List<ResumeSection> sections = new ArrayList<>();
        for (String label : groupLabels) {
            List<String> names = new ArrayList<>();
            for (String source : groupByLabel.get(label).sources()) {
                names.addAll(orderCategory(source, included, categoryOf, stacks.get(0)));
            }
            if (!names.isEmpty()) {
                sections.add(new ResumeSection(label, names));
            }
        }
        return new Selection(sections, unmatched);
    }

    // ---- selection --------------------------------------------------------------------------

    /** Rules 2, 5, 6 and 7 (exclude): which skills are in the resume and why. */
    private Map<String, Reason> includeSkills(Run run) {
        Map<String, Reason> included = new LinkedHashMap<>();
        for (Skill s : skills.values()) {
            if (run.named(s.name())) {
                included.put(s.name(), Reason.JD);
            } else if (run.eval(s.include())) {
                included.put(s.name(), s.baseReason());
            }
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (String name : new ArrayList<>(included.keySet())) {
                Skill s = skills.get(name);
                for (String required : s.requires()) {
                    changed |= included.putIfAbsent(required, Reason.DEP) == null;
                }
                if (included.get(name) == Reason.JD) {
                    for (String pulled : s.pulls()) {
                        changed |= included.putIfAbsent(pulled, Reason.PULLED) == null;
                    }
                }
            }
        }

        for (Skill s : skills.values()) {
            if (included.containsKey(s.name()) && s.exclude() != null && run.eval(s.exclude())) {
                included.remove(s.name());
            }
        }
        return included;
    }

    /** Rule 7 (moveTo): the category each included skill is shown in. */
    private Map<String, String> moveSkills(Run run, Map<String, Reason> included) {
        Map<String, String> categoryOf = new HashMap<>();
        for (Skill s : skills.values()) {
            categoryOf.put(s.name(), s.category());
            if (!included.containsKey(s.name())) {
                continue;
            }
            for (Map<String, Object> move : s.moveTo()) {
                if (run.eval(move.get("when"))) {
                    categoryOf.put(s.name(), (String) move.get("category"));
                    break;
                }
            }
        }
        return categoryOf;
    }

    /** Rule 9: order of the skills inside one category. */
    private List<String> orderCategory(String category, Map<String, Reason> included, Map<String, String> categoryOf, String primaryStack) {
        List<Skill> items = new ArrayList<>();
        for (Skill s : skills.values()) {
            if (included.containsKey(s.name()) && category.equals(categoryOf.get(s.name()))) {
                items.add(s);
            }
        }
        // Two skills the job names tie (a Vue role that says "Vue (or React)"): the one specific to the job's main stack goes first.
        // Nothing else is reordered: every other tie is still decided by the order of the skills file.
        items.sort(Comparator
                .comparingInt((Skill s) -> included.get(s.name()).rank)
                .thenComparingInt((Skill s) -> included.get(s.name()) == Reason.JD && specificToStack(s.include(), primaryStack) ? 0 : 1)
                .thenComparingInt(Skill::indexInCategory));

        List<String> ordered = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        for (Skill s : items) {
            addWithPrerequisites(s, category, included, categoryOf, ordered, visiting);
        }
        return ordered;
    }

    /** Whether a skill's include rule is about this stack and not about every stack (React and Vue are; JavaScript, listed for all, is not). */
    private boolean specificToStack(Object condition, String stack) {
        if (!(condition instanceof Map<?, ?> map) || map.size() != 1) {
            return false;
        }
        Map.Entry<?, ?> op = map.entrySet().iterator().next();
        return switch (String.valueOf(op.getKey())) {
            case "stack" -> strings(op.getValue()).contains(stack) && strings(op.getValue()).size() < groupOrder.size();
            case "all", "any" -> asList(op.getValue()).stream().anyMatch(c -> specificToStack(c, stack));
            default -> false;
        };
    }

    /** Same-category prerequisites always come before the skill that needs them. */
    private void addWithPrerequisites(Skill s, String category, Map<String, Reason> included,
                                      Map<String, String> categoryOf, List<String> ordered, Set<String> visiting) {
        if (ordered.contains(s.name()) || !visiting.add(s.name())) {
            return;
        }
        for (String required : s.requires()) {
            if (included.containsKey(required) && category.equals(categoryOf.get(required))) {
                addWithPrerequisites(skills.get(required), category, included, categoryOf, ordered, visiting);
            }
        }
        ordered.add(s.name());
    }

    /** State of one {@link #select} call: the picked stacks and the skills the job named. */
    private final class Run {
        private final Set<String> stacks;
        private final Set<String> namedSkills;

        Run(Set<String> stacks, Set<String> namedSkills) {
            this.stacks = stacks;
            this.namedSkills = namedSkills;
        }

        boolean named(String skillName) {
            return namedSkills.contains(skillName);
        }

        boolean eval(Object condition) {
            if (condition instanceof Boolean b) {
                return b;
            }
            Map<String, Object> map = asMap(condition);
            if (map.size() != 1) {
                throw new IllegalStateException("A condition needs exactly one operator: " + map);
            }
            Map.Entry<String, Object> op = map.entrySet().iterator().next();
            Object arg = op.getValue();
            return switch (op.getKey()) {
                case "stack" -> strings(arg).stream().anyMatch(stacks::contains);
                case "notStack" -> strings(arg).stream().noneMatch(stacks::contains);
                case "named" -> strings(arg).stream().anyMatch(this::named);
                case "namedAll" -> strings(arg).stream().allMatch(this::named);
                case "notNamed" -> strings(arg).stream().noneMatch(this::named);
                case "all" -> asList(arg).stream().allMatch(this::eval);
                case "any" -> asList(arg).stream().anyMatch(this::eval);
                case "not" -> !eval(arg);
                default -> throw new IllegalStateException("Unknown condition operator: " + op.getKey());
            };
        }
    }

    // ---- loading and validation -------------------------------------------------------------

    private static Skill toSkill(String category, int index, Map<String, Object> m) {
        String name = (String) m.get("name");

        Set<String> keys = new LinkedHashSet<>();
        keys.add(normalize(name));
        strings(m.get("aliases")).forEach(alias -> keys.add(normalize(alias)));

        List<Map<String, Object>> moveTo = new ArrayList<>();
        asList(m.get("moveTo")).forEach(move -> moveTo.add(asMap(move)));

        return new Skill(
                name,
                keys,
                m.getOrDefault("include", false),
                "related".equals(m.get("rank")) ? Reason.RELATED : Reason.CORE,
                strings(m.get("requires")),
                strings(m.get("pulls")),
                m.get("exclude"),
                moveTo,
                category,
                index
        );
    }

    /** Keywords are compared ignoring case and extra whitespace. */
    private static String normalize(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Fails fast on a typo in Meta.json instead of silently mis-selecting skills. */
    private void validate() {
        Set<String> sources = new HashSet<>();
        Set<String> labels = new HashSet<>();
        for (Group g : groups) {
            labels.add(g.label());
            sources.addAll(g.sources());
        }
        groupOrder.forEach((stack, order) -> {
            if (order.size() != labels.size() || !labels.containsAll(order)) {
                throw new IllegalStateException("groupOrder[" + stack + "] must list every output group once");
            }
        });
        if (!labels.contains(promotedGroup)) {
            throw new IllegalStateException("Unknown promoted group: " + promotedGroup);
        }

        for (Skill s : skills.values()) {
            if (!sources.contains(s.category())) {
                throw new IllegalStateException(s.name() + ": category not in any output group: " + s.category());
            }
            s.requires().forEach(r -> requireSkill(s, r));
            s.pulls().forEach(r -> requireSkill(s, r));
            validateCondition(s, s.include());
            if (s.exclude() != null) {
                validateCondition(s, s.exclude());
            }
            for (Map<String, Object> move : s.moveTo()) {
                validateCondition(s, move.get("when"));
                if (!sources.contains((String) move.get("category"))) {
                    throw new IllegalStateException(s.name() + ": moveTo unknown category " + move.get("category"));
                }
            }
        }
    }

    private void requireSkill(Skill owner, String name) {
        if (!skills.containsKey(name)) {
            throw new IllegalStateException(owner.name() + " refers to unknown skill: " + name);
        }
    }

    private void validateCondition(Skill owner, Object condition) {
        if (condition instanceof Boolean) {
            return;
        }
        Map<String, Object> map = asMap(condition);
        if (map.size() != 1) {
            throw new IllegalStateException(owner.name() + ": a condition needs exactly one operator: " + map);
        }
        Map.Entry<String, Object> op = map.entrySet().iterator().next();
        switch (op.getKey()) {
            case "stack", "notStack" -> strings(op.getValue());
            case "named", "namedAll", "notNamed" -> strings(op.getValue()).forEach(n -> requireSkill(owner, n));
            case "all", "any" -> asList(op.getValue()).forEach(c -> validateCondition(owner, c));
            case "not" -> validateCondition(owner, op.getValue());
            default -> throw new IllegalStateException(owner.name() + ": unknown condition operator " + op.getKey());
        }
    }

    // ---- plain-JSON helpers -----------------------------------------------------------------

    private static Object read(InputStream in) {
        return JsonMapper.builder().build().readValue(in, Object.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw new IllegalStateException("Expected a JSON object but got: " + o);
    }

    private static List<Object> asList(Object o) {
        if (o == null) {
            return List.of();
        }
        if (o instanceof List<?> l) {
            return new ArrayList<>(l);
        }
        throw new IllegalStateException("Expected a JSON array but got: " + o);
    }

    private static List<String> strings(Object o) {
        List<String> result = new ArrayList<>();
        asList(o).forEach(e -> result.add((String) e));
        return result;
    }
}
