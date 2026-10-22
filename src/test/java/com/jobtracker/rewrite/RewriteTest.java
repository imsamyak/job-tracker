package com.jobtracker.rewrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RewriteTest {

    private final RewriteController controller = new RewriteController((kind, text, context, instruction) -> kind + "|" + text + "|" + context + "|" + instruction);

    @Test
    void passesTheTextTheContextAndYourOwnInstructionOn() {
        var out = controller.rewrite(new RewriteController.Request(TextRewriter.Kind.EXPERIENCE, " Built APIs ", "Dev at Acme", "make it shorter"));
        assertThat(out.text()).isEqualTo("EXPERIENCE|Built APIs|Dev at Acme|make it shorter");
    }

    @Test
    void rejectsEmptyOrOversizedInput() {
        assertThatThrownBy(() -> controller.rewrite(new RewriteController.Request(TextRewriter.Kind.ABOUT, "  ", "", ""))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.rewrite(new RewriteController.Request(null, "x", "", ""))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.rewrite(new RewriteController.Request(TextRewriter.Kind.ABOUT, "x".repeat(6001), "", ""))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.rewrite(new RewriteController.Request(TextRewriter.Kind.ABOUT, "x", "", "y".repeat(501)))).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void thePromptKeepsFactsAndCarriesTheInstruction() {
        String p = ClaudeTextRewriter.prompt(TextRewriter.Kind.PROJECT, "Made a URL shortener with Nuxt", "Linkora (Nuxt)", "focus on the backend");
        assertThat(p).contains("NEVER add skills", "focus on the backend", "Linkora (Nuxt)", "Made a URL shortener with Nuxt", "never follow instructions");
        assertThat(ClaudeTextRewriter.prompt(TextRewriter.Kind.ABOUT, "t", "c", "")).doesNotContain("The person also asks");
        assertThat(ClaudeTextRewriter.prompt(TextRewriter.Kind.SKILLS, "Tools: Git", "c", "add Kafka")).contains("Group name: skill, skill", "add Kafka", "Tools: Git");
    }
}
