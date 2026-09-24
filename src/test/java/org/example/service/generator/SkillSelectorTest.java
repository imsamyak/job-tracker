package org.example.service.generator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillSelectorTest {

    private final SkillSelector selector = SkillSelector.load();

    /** Cases live in skill-selector-cases.txt: "@@ stacks | keywords" followed by "Group: a, b" lines. */
    @Test
    void producesTheExpectedResumeForEveryCase() throws IOException {
        String cases;
        try (InputStream in = getClass().getResourceAsStream("/skill-selector-cases.txt")) {
            cases = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        int checked = 0;
        for (String block : cases.split("\\R\\R+")) {
            if (block.isBlank()) {
                continue;
            }
            List<String> lines = block.strip().lines().toList();
            String[] header = lines.get(0).substring(3).split(" \\| ");
            List<String> stacks = Arrays.asList(header[0].split(","));
            List<String> keywords = Arrays.asList(header[1].split(", "));

            List<String> actual = new ArrayList<>();
            selector.select(stacks, keywords).sections()
                    .forEach(s -> actual.add(s.label() + ": " + String.join(", ", s.skills())));

            assertEquals(lines.subList(1, lines.size()), actual, "case: " + lines.get(0));
            checked++;
        }
        assertEquals(10, checked);
    }

    @Test
    void keywordsAreMatchedByAliasIgnoringCase() {
        var messaging = selector.select(List.of("java"), List.of("apache kafka", "  K8S ")).sections();

        assertTrue(messaging.stream().anyMatch(s -> s.label().equals("Messaging") && s.skills().contains("Kafka")));
        assertTrue(messaging.stream().anyMatch(s -> s.skills().contains("Kubernetes")));
    }

    @Test
    void unknownKeywordsAreReportedAndNeverAdded() {
        var selection = selector.select(List.of("java"), List.of("Java", "Terraform", " "));

        assertEquals(List.of("Terraform"), selection.unmatched());
        assertTrue(selection.sections().stream().noneMatch(s -> s.skills().contains("Terraform")));
    }

    @Test
    void javascriptDoesNotNameJava() {
        var sections = selector.select(List.of("react"), List.of("JavaScript")).sections();

        assertTrue(sections.stream().noneMatch(s -> s.skills().contains("Java")));
    }

    @Test
    void rejectsMissingOrUnknownStacks() {
        assertThrows(IllegalArgumentException.class, () -> selector.select(List.of(), List.of("Java")));
        assertThrows(IllegalArgumentException.class, () -> selector.select(List.of("cobol"), List.of("Java")));
    }
}
