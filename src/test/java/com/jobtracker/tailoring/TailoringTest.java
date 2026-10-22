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
}
