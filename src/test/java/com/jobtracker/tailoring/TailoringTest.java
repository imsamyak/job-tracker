package com.jobtracker.tailoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The module through its front door, the way the app uses it. */
class TailoringTest {

    private final Tailoring tailoring = Tailoring.load();

    @Test
    void knowsTheKindsOfRoleAndTheSkills() {
        assertThat(tailoring.stacks()).containsExactlyInAnyOrder("react", "vue", "node", "java", "cloud");
        assertThat(tailoring.knownSkills()).contains("Java", "Kafka").isNotEmpty();
    }

    @Test
    void picksSkillsForAJavaRoleAndReportsWhatItDoesNotKnow() {
        Tailoring.Skills skills = tailoring.skillsFor(List.of("java"), List.of("Java", "Kafka", "COBOL"));

        assertThat(skills.sections()).isNotEmpty();
        List<String> all = skills.sections().stream().flatMap(s -> s.skills().stream()).toList();
        assertThat(all).contains("Java", "Kafka");
        assertThat(all).doesNotContain("COBOL"); // a skill that is not on your list is never invented
        assertThat(skills.unmatched()).containsExactly("COBOL");
    }

    @Test
    void readsAJavaJobFromItsDescriptionWithoutAnyAi() {
        Tailoring.Tailored t = tailoring.tailor("We need a backend engineer with Java, Spring Boot and Kafka experience. AWS is a plus.", List.of());
        assertThat(t.stacks()).contains("java");
        List<String> all = t.skills().sections().stream().flatMap(s -> s.skills().stream()).toList();
        assertThat(all).contains("Java", "Kafka");
    }

    @Test
    void aFrontendJobLeadsWithItsOwnStack() {
        Tailoring.Tailored t = tailoring.tailor("Senior Frontend Engineer. You will build a component library in Vue 3, Nuxt and TypeScript.", List.of());
        assertThat(t.stacks().get(0)).isEqualTo("vue");
    }

    @Test
    void aSkillTheJobNamesIsListedFirstInItsGroup() {
        // the job names Vue and Nuxt, so they lead the frontend line (and what Vue pulls in, Pinia, follows)
        Tailoring.Tailored t = tailoring.tailor("Senior Frontend Engineer. You will build a component library in Vue 3, Nuxt and TypeScript with Web Components.", List.of());
        List<String> frontend = t.skills().sections().stream().filter(s -> s.label().startsWith("Frontend")).findFirst().orElseThrow().skills();
        assertThat(frontend.get(0)).isEqualTo("Vue.js");
        assertThat(frontend).contains("Nuxt.js", "Pinia").doesNotContain("React.js");
    }

    @Test
    void namesLikeJavaScriptAreNotReportedAsUnknown() {
        Tailoring.Tailored t = tailoring.tailor("Backend role in Java. Some JavaScript is useful. Postman and Docker.", List.of());
        assertThat(t.skills().unmatched()).doesNotContain("JavaScript");
    }

    @Test
    void whenTwoSkillsAreBothNamedTheMainStacksOneComesFirst() {
        // "Vue (or React)": both are named, but this is a Vue role, so Vue.js leads
        Tailoring.Tailored vue = tailoring.tailor("Senior Frontend Engineer. Strong Vue.js and Nuxt (or React) experience.", List.of());
        List<String> frontend = vue.skills().sections().stream().filter(s -> s.label().startsWith("Frontend")).findFirst().orElseThrow().skills();
        assertThat(vue.stacks().get(0)).isEqualTo("vue");
        assertThat(frontend.indexOf("Vue.js")).isGreaterThanOrEqualTo(0).isLessThan(frontend.indexOf("React.js"));

        // the same two skills named in a React role: React leads
        Tailoring.Tailored react = tailoring.tailor("Frontend Engineer. Strong React, Redux and Next.js (or Vue) experience.", List.of());
        List<String> frontend2 = react.skills().sections().stream().filter(s -> s.label().startsWith("Frontend")).findFirst().orElseThrow().skills();
        assertThat(react.stacks().get(0)).isEqualTo("react");
        assertThat(frontend2.indexOf("React.js")).isGreaterThanOrEqualTo(0).isLessThan(frontend2.indexOf("Vue.js"));
    }

    @Test
    void restApisAndSolidAreRecognisedAsSkillsOnTheList() {
        Tailoring.Tailored t = tailoring.tailor("Backend role in Java using REST APIs and SOLID design.", List.of());
        assertThat(t.skills().unmatched()).doesNotContain("REST APIs", "SOLID");
    }

    @Test
    void aDescriptionThatSaysNothingAboutTheStackGivesNoLinesNotAGuess() {
        Tailoring.Tailored t = tailoring.tailor("We are a friendly team. Apply today.", List.of("Communication"));
        assertThat(t.stacks()).isEmpty();
        assertThat(t.skills().sections()).isEmpty();
        assertThat(t.skills().unmatched()).containsExactly("Communication");
    }

    @Test
    void skillsAnAiReadOutAreUsedToo() {
        Tailoring.Tailored t = tailoring.tailor("Backend role using Java.", List.of("Kafka"));
        assertThat(t.skills().sections().stream().flatMap(s -> s.skills().stream()).toList()).contains("Kafka");
    }

    @Test
    void refusesAnEmptyOrUnknownMainStack() {
        assertThatThrownBy(() -> tailoring.skillsFor(List.of(), List.of("Java"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tailoring.skillsFor(List.of("cobol"), List.of("Java"))).isInstanceOf(IllegalArgumentException.class);
    }

    private static final String JD = "Backend engineer. Java, Spring Boot and Kafka required. AWS is a plus.";

    @Test
    void bulletsThatNameTheJobsSkillsComeFirstAndNothingIsRewritten() {
        List<String> bullets = List.of(
                "Organised the team offsite and weekly demos.",
                "Built REST services in Java and Spring Boot.",
                "Moved order events onto Kafka with Java consumers and Spring Boot.");
        var out = tailoring.pickBullets(JD, List.of(), List.of(new Tailoring.BulletSource("a", bullets)), 20);

        List<String> texts = out.get(0).bullets().stream().map(Tailoring.Bullet::text).toList();
        assertThat(texts).containsExactly(bullets.get(2), bullets.get(1), bullets.get(0)); // verbatim, best first
        assertThat(out.get(0).bullets().get(0).named()).contains("Java", "Kafka");
    }

    @Test
    void anEarlierBulletWinsATie() {
        List<String> bullets = List.of("Wrote Java services for billing.", "Wrote Java services for search.");
        var out = tailoring.pickBullets(JD, List.of(), List.of(new Tailoring.BulletSource("a", bullets)), 20);
        assertThat(out.get(0).bullets()).extracting(Tailoring.Bullet::text).containsExactlyElementsOf(bullets);
    }

    @Test
    void dropsTheWeakestBulletsWhenThePageIsFull() {
        String strong = "Built Java and Kafka pipelines.";
        String weak = "Organised the team offsite.";
        var out = tailoring.pickBullets(JD, List.of(), List.of(new Tailoring.BulletSource("a", List.of(weak, strong))), 1);
        assertThat(out.get(0).bullets()).extracting(Tailoring.Bullet::text).containsExactly(strong);
    }

    @Test
    void everyEntryGetsItsBestBulletBeforeAnyEntryGetsASecond() {
        var a = new Tailoring.BulletSource("a", List.of("Java one.", "Java two."));
        var b = new Tailoring.BulletSource("b", List.of("Kafka one."));
        var out = tailoring.pickBullets(JD, List.of(), List.of(a, b), 2);
        assertThat(out.get(0).bullets()).hasSize(1);
        assertThat(out.get(1).bullets()).hasSize(1);
    }

    @Test
    void extraKeywordsFromTheIntakeCountAsNamed() {
        var out = tailoring.pickBullets("Engineer", List.of("Kafka"),
                List.of(new Tailoring.BulletSource("a", List.of("Organised demos.", "Moved events onto Kafka."))), 20);
        assertThat(out.get(0).bullets().get(0).text()).isEqualTo("Moved events onto Kafka.");
    }

    @Test
    void blankBulletsAndMissingListsAreIgnored() {
        var out = tailoring.pickBullets(JD, null, List.of(new Tailoring.BulletSource("a", List.of("", "  ")), new Tailoring.BulletSource("b", null)), 10);
        assertThat(out).allSatisfy(e -> assertThat(e.bullets()).isEmpty());
    }

    @Test
    void summaryVariantFollowsThePrimaryStack() {
        assertThat(Tailoring.summaryVariant(List.of("java"))).isEqualTo("backend");
        assertThat(Tailoring.summaryVariant(List.of("node"))).isEqualTo("backend");
        assertThat(Tailoring.summaryVariant(List.of("react"))).isEqualTo("frontend");
        assertThat(Tailoring.summaryVariant(List.of("vue", "java"))).isEqualTo("full-stack");
        assertThat(Tailoring.summaryVariant(List.of("java", "react"))).isEqualTo("full-stack");
        assertThat(Tailoring.summaryVariant(List.of("cloud"))).isEqualTo("cloud");
        assertThat(Tailoring.summaryVariant(List.of())).isNull();
    }
}
